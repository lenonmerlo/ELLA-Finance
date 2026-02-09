package com.ella.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import com.ella.backend.dto.dashboard.DashboardRequestDTO;
import com.ella.backend.entities.Person;
import com.ella.backend.enums.TransactionStatus;
import com.ella.backend.enums.TransactionType;
import com.ella.backend.repositories.CompanyRepository;
import com.ella.backend.repositories.FinancialTransactionRepository;
import com.ella.backend.repositories.GoalRepository;
import com.ella.backend.repositories.InstallmentRepository;
import com.ella.backend.repositories.InvoiceRepository;
import com.ella.backend.repositories.PersonRepository;

@ExtendWith(MockitoExtension.class)
class DashboardServiceOptimizedTest {

    @Mock
    PersonRepository personRepository;

    @Mock
    CompanyRepository companyRepository;

    @Mock
    FinancialTransactionRepository financialTransactionRepository;

    @Mock
    GoalRepository goalRepository;

    @Mock
    InvoiceRepository invoiceRepository;

    @Mock
    InstallmentRepository installmentRepository;

    @Test
    void buildDashboard_usesAggregatesAndLimitsTransactionList() {
        UUID personId = UUID.randomUUID();
        Person person = new Person();
        person.setId(personId);
        person.setName("Test User");

        when(personRepository.findById(personId)).thenReturn(Optional.of(person));
        when(companyRepository.findByOwner(person)).thenReturn(List.of());
        when(goalRepository.findByOwner(person)).thenReturn(List.of());
        when(invoiceRepository.findByCardOwnerAndMonthAndYearAndDeletedAtIsNull(eq(person), eq(2), eq(2026)))
                .thenReturn(List.of());

        when(financialTransactionRepository.sumAmountByPersonAndDateRangeAndType(eq(person), any(), any(), eq(TransactionType.INCOME)))
                .thenReturn(new BigDecimal("1000.00"));
        when(financialTransactionRepository.sumAmountByPersonAndDateRangeAndType(eq(person), any(), any(), eq(TransactionType.EXPENSE)))
                .thenReturn(new BigDecimal("250.00"));

        FinancialTransactionRepository.CategoryTotalProjection cat1 = new FinancialTransactionRepository.CategoryTotalProjection() {
            @Override public String getCategory() { return "Alimentacao"; }
            @Override public BigDecimal getTotal() { return new BigDecimal("150.00"); }
        };
        FinancialTransactionRepository.CategoryTotalProjection cat2 = new FinancialTransactionRepository.CategoryTotalProjection() {
            @Override public String getCategory() { return "Transporte"; }
            @Override public BigDecimal getTotal() { return new BigDecimal("100.00"); }
        };
        when(financialTransactionRepository.sumExpenseTotalsByCategoryForPersonAndDateRange(eq(person), any(), any()))
                .thenReturn(List.of(cat1, cat2));

        FinancialTransactionRepository.MonthTypeTotalProjection janIncome = new FinancialTransactionRepository.MonthTypeTotalProjection() {
            @Override public LocalDate getMonthStart() { return LocalDate.of(2026, 1, 1); }
            @Override public String getType() { return "INCOME"; }
            @Override public BigDecimal getTotal() { return new BigDecimal("500.00"); }
        };
        FinancialTransactionRepository.MonthTypeTotalProjection janExpense = new FinancialTransactionRepository.MonthTypeTotalProjection() {
            @Override public LocalDate getMonthStart() { return LocalDate.of(2026, 1, 1); }
            @Override public String getType() { return "EXPENSE"; }
            @Override public BigDecimal getTotal() { return new BigDecimal("200.00"); }
        };
        when(financialTransactionRepository.sumTotalsByMonthAndTypeForPersonAndDateRange(eq(personId), any(), any()))
                .thenReturn(List.of(janIncome, janExpense));

        // Fake page with more items than the configured limit.
        var txs = java.util.stream.IntStream.range(0, 20)
                .mapToObj(i -> com.ella.backend.entities.FinancialTransaction.builder()
                        .id(UUID.randomUUID())
                        .person(person)
                        .description("TX " + i)
                        .amount(BigDecimal.ONE)
                        .type(TransactionType.EXPENSE)
                        .category("Outros")
                        .transactionDate(LocalDate.of(2026, 2, 1))
                        .status(TransactionStatus.PENDING)
                        .build())
                .toList();

        Page<com.ella.backend.entities.FinancialTransaction> txPage = new PageImpl<>(txs);
        when(financialTransactionRepository.findByPersonAndTransactionDateBetweenAndDeletedAtIsNull(eq(person), any(), any(), any()))
                .thenReturn(txPage);

        DashboardService service = new DashboardService(
                personRepository,
                companyRepository,
                financialTransactionRepository,
                goalRepository,
                invoiceRepository,
                installmentRepository
        );
        ReflectionTestUtils.setField(service, "personalTransactionsLimit", 10);

        DashboardRequestDTO request = DashboardRequestDTO.builder()
                .personId(personId.toString())
                .year(2026)
                .month(2)
                .build();

        var response = service.buildDashboard(request);

        assertThat(response.getPersonalSummary().getTotalIncome()).isEqualByComparingTo("1000.00");
        assertThat(response.getPersonalSummary().getTotalExpenses()).isEqualByComparingTo("250.00");
        assertThat(response.getPersonalTotals().getYearIncome()).isEqualByComparingTo("1000.00");
        assertThat(response.getPersonalTotals().getYearExpenses()).isEqualByComparingTo("250.00");

        assertThat(response.getPersonalCategoryBreakdown()).hasSize(2);
        assertThat(response.getPersonalMonthlyEvolution().getPoints()).hasSize(12);

        // Limited payload
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(financialTransactionRepository)
                .findByPersonAndTransactionDateBetweenAndDeletedAtIsNull(eq(person), any(), any(), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(10);

        // The service may map/sort, but should not return more than limit.
        assertThat(response.getPersonalTransactions().size()).isLessThanOrEqualTo(10);

        // Ensure we no longer load full month/year transaction lists.
        verify(financialTransactionRepository, never())
                .findByPersonAndTransactionDateBetweenAndDeletedAtIsNull(eq(person), any(LocalDate.class), any(LocalDate.class));
    }
}
