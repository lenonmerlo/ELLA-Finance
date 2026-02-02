package com.ella.backend.services.goals.providers;

import static org.junit.jupiter.api.Assertions.assertFalse;
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

class BudgetOptimizationGoalProviderV2Test {

    @Test
    @DisplayName("Generates budget goals for high-variation reducible categories")
    void generatesForHighVariationCategories() {
        Person person = new Person();
        person.setId(UUID.randomUUID());
        person.setName("Test");

        YearMonth current = YearMonth.from(LocalDate.now());

        List<FinancialTransaction> txs = List.of(
                expense(person, current.minusMonths(2).atDay(10), "100", "Lazer"),
                expense(person, current.minusMonths(1).atDay(10), "300", "Lazer"),
                expense(person, current.atDay(10), "200", "Lazer"),
                expense(person, current.minusMonths(2).atDay(12), "200", "Mercado"),
                expense(person, current.minusMonths(1).atDay(12), "210", "Mercado"),
                expense(person, current.atDay(12), "205", "Mercado")
        );

        BudgetOptimizationGoalProvider provider = new BudgetOptimizationGoalProvider();
        List<Goal> goals = provider.generateGoals(person, txs);

        assertFalse(goals.isEmpty());
        assertTrue(goals.stream().allMatch(g -> g.getTitle() != null && g.getTitle().startsWith("Orçamento:")));
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
