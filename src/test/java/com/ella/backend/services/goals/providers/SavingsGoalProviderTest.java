package com.ella.backend.services.goals.providers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ella.backend.entities.FinancialTransaction;
import com.ella.backend.entities.Goal;
import com.ella.backend.entities.Person;
import com.ella.backend.enums.TransactionStatus;
import com.ella.backend.enums.TransactionType;

class EmergencyFundGoalProviderTest {

        @Test
        @DisplayName("Generates emergency fund goal when expenses are meaningful")
        void generatesEmergencyFundGoal() {
        Person person = new Person();
        person.setId(UUID.randomUUID());
        person.setName("Test");

        YearMonth current = YearMonth.from(LocalDate.now());

        List<FinancialTransaction> txs = List.of(
                                income(person, current.atDay(5), "6000"),
                                income(person, current.minusMonths(1).atDay(5), "6000"),
                                income(person, current.minusMonths(2).atDay(5), "6000"),
                                expense(person, current.atDay(10), "2500", "Mercado"),
                                expense(person, current.minusMonths(1).atDay(10), "2500", "Mercado"),
                                expense(person, current.minusMonths(2).atDay(10), "2500", "Mercado")
        );

                EmergencyFundGoalProvider provider = new EmergencyFundGoalProvider();
                List<Goal> goals = provider.generateGoals(person, txs);

                assertEquals(1, goals.size());
                assertTrue(goals.getFirst().getTargetAmount().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
        @DisplayName("Does not generate emergency fund goal when expenses are too low")
        void doesNotGenerateWhenLowExpenses() {
        Person person = new Person();
        person.setId(UUID.randomUUID());
        person.setName("Test");

        YearMonth current = YearMonth.from(LocalDate.now());

        List<FinancialTransaction> txs = List.of(
                income(person, current.atDay(5), "3000"),
                income(person, current.minusMonths(1).atDay(5), "3000"),
                income(person, current.minusMonths(2).atDay(5), "3000"),
                expense(person, current.atDay(10), "100", "Lanches"),
                expense(person, current.minusMonths(1).atDay(10), "100", "Lanches"),
                expense(person, current.minusMonths(2).atDay(10), "100", "Lanches")
        );

        EmergencyFundGoalProvider provider = new EmergencyFundGoalProvider();
        List<Goal> goals = provider.generateGoals(person, txs);
        assertTrue(goals.isEmpty());
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
