package com.ella.backend.services.insights.providers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ella.backend.dto.dashboard.InsightDTO;
import com.ella.backend.entities.FinancialTransaction;
import com.ella.backend.entities.Person;
import com.ella.backend.enums.TransactionStatus;
import com.ella.backend.enums.TransactionType;
import com.ella.backend.repositories.FinancialTransactionRepository;
import com.ella.backend.services.cashflow.CashflowTransactionsService;
import com.ella.backend.services.insights.InsightDataCache;

@ExtendWith(MockitoExtension.class)
class CashflowSummaryInsightProviderTest {

    @Mock
    private FinancialTransactionRepository financialTransactionRepository;

    @Mock
    private CashflowTransactionsService cashflowTransactionsService;

    @Test
    @DisplayName("Generates cashflow summary using combined cashflow transactions")
    void generatesCashflowSummary() {
        Person person = new Person();
        person.setId(UUID.randomUUID());
        person.setName("Test");

        YearMonth ym = YearMonth.of(2026, 2);

        List<FinancialTransaction> combined = List.of(
                tx(person, LocalDate.of(2026, 2, 1), TransactionType.INCOME, "Salário", "5000.00"),
                tx(person, LocalDate.of(2026, 2, 2), TransactionType.EXPENSE, "Mercado", "1200.00")
        );

        when(cashflowTransactionsService.fetchCashflowTransactions(eq(person), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(combined);

        InsightDataCache cache = new InsightDataCache(financialTransactionRepository, cashflowTransactionsService);
        CashflowSummaryInsightProvider provider = new CashflowSummaryInsightProvider(cache);

        List<InsightDTO> insights = provider.generate(person, ym.getYear(), ym.getMonthValue());

        assertEquals(1, insights.size());
        assertEquals("Conta corrente", insights.getFirst().getCategory());
        String msg = insights.getFirst().getMessage().toLowerCase();
        assertTrue(msg.contains("entradas"));
        assertTrue(msg.contains("saídas"));
        assertTrue(msg.contains("resultado"));
    }

    private static FinancialTransaction tx(Person person, LocalDate date, TransactionType type, String desc, String amount) {
        return FinancialTransaction.builder()
                .person(person)
                .transactionDate(date)
                .purchaseDate(date)
                .category("Outros")
                .description(desc)
                .amount(new BigDecimal(amount))
                .type(type)
                .status(TransactionStatus.PAID)
                .build();
    }
}
