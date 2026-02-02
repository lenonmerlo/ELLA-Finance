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

class SubscriptionCleanupGoalProviderTest {

    @Test
    @DisplayName("Generates subscription cleanup goal when 3+ recurring subscriptions exist")
    void generatesSubscriptionCleanupGoal() {
        Person person = new Person();
        person.setId(UUID.randomUUID());
        person.setName("Test");

        YearMonth current = YearMonth.from(LocalDate.now());

        List<FinancialTransaction> txs = List.of(
                subExpense(person, current.atDay(5), "29.90", "NETFLIX.COM"),
                subExpense(person, current.minusMonths(1).atDay(5), "29.90", "Netflix"),
                subExpense(person, current.minusMonths(2).atDay(5), "29.90", "Netflix"),
                subExpense(person, current.atDay(8), "19.90", "Spotify"),
                subExpense(person, current.minusMonths(1).atDay(8), "19.90", "SPOTIFY"),
                subExpense(person, current.minusMonths(2).atDay(8), "19.90", "Spotify"),
                subExpense(person, current.atDay(12), "14.90", "Amazon Prime"),
                subExpense(person, current.minusMonths(1).atDay(12), "14.90", "AMAZON PRIME"),
                subExpense(person, current.minusMonths(2).atDay(12), "14.90", "Amazon Prime")
        );

        SubscriptionCleanupGoalProvider provider = new SubscriptionCleanupGoalProvider();
        List<Goal> goals = provider.generateGoals(person, txs);

        assertEquals(1, goals.size());
        assertTrue(goals.getFirst().getTitle().toLowerCase().contains("assin"));
        assertTrue(goals.getFirst().getTargetAmount().compareTo(BigDecimal.ZERO) > 0);
    }

    private static FinancialTransaction subExpense(Person person, LocalDate date, String amount, String description) {
        return FinancialTransaction.builder()
                .person(person)
                .transactionDate(date)
                .purchaseDate(date)
                .description(description)
                .category("Assinaturas")
                .amount(new BigDecimal(amount))
                .type(TransactionType.EXPENSE)
                .status(TransactionStatus.PAID)
                .build();
    }
}
