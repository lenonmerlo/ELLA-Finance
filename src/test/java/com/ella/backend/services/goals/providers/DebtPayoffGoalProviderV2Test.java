package com.ella.backend.services.goals.providers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

import com.ella.backend.entities.FinancialTransaction;
import com.ella.backend.entities.Goal;
import com.ella.backend.entities.Invoice;
import com.ella.backend.entities.Person;
import com.ella.backend.enums.InvoiceStatus;
import com.ella.backend.enums.TransactionStatus;
import com.ella.backend.enums.TransactionType;
import com.ella.backend.repositories.InvoiceRepository;

@ExtendWith(MockitoExtension.class)
class DebtPayoffGoalProviderV2Test {

    @Mock
    private InvoiceRepository invoiceRepository;

    @Test
    @DisplayName("Generates debt payoff goal when open invoices exceed threshold")
    void generatesDebtPayoffGoal() {
        Person person = new Person();
        person.setId(UUID.randomUUID());
        person.setName("Test");

        Invoice invoice = new Invoice();
        invoice.setStatus(InvoiceStatus.OPEN);
        invoice.setTotalAmount(new BigDecimal("1000"));
        invoice.setPaidAmount(new BigDecimal("200"));
        invoice.setMonth(YearMonth.now().getMonthValue());
        invoice.setYear(YearMonth.now().getYear());
        invoice.setDueDate(LocalDate.now().plusDays(10));

        when(invoiceRepository.findByCardOwner(person)).thenReturn(List.of(invoice));

        List<FinancialTransaction> txs = List.of(
                income(person, LocalDate.now().minusMonths(2).withDayOfMonth(5), "6000"),
                income(person, LocalDate.now().minusMonths(1).withDayOfMonth(5), "6000"),
                income(person, LocalDate.now().withDayOfMonth(5), "6000"),
                expense(person, LocalDate.now().minusMonths(2).withDayOfMonth(10), "4000", "Geral"),
                expense(person, LocalDate.now().minusMonths(1).withDayOfMonth(10), "4000", "Geral"),
                expense(person, LocalDate.now().withDayOfMonth(10), "4000", "Geral")
        );

        DebtPayoffGoalProvider provider = new DebtPayoffGoalProvider(invoiceRepository);
        List<Goal> goals = provider.generateGoals(person, txs);

        assertEquals(1, goals.size());
        assertTrue(goals.getFirst().getTargetAmount().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(goals.getFirst().getDescription().toLowerCase().contains("faturas"));
    }

    private static FinancialTransaction income(Person person, LocalDate date, String amount) {
        return FinancialTransaction.builder()
                .person(person)
                .transactionDate(date)
                .purchaseDate(date)
                .description("Income")
                .category("Renda")
                .amount(new BigDecimal(amount))
                .type(TransactionType.INCOME)
                .status(TransactionStatus.PAID)
                .build();
    }

    private static FinancialTransaction expense(Person person, LocalDate date, String amount, String category) {
        return FinancialTransaction.builder()
                .person(person)
                .transactionDate(date)
                .purchaseDate(date)
                .description("Expense")
                .category(category)
                .amount(new BigDecimal(amount))
                .type(TransactionType.EXPENSE)
                .status(TransactionStatus.PAID)
                .build();
    }
}
