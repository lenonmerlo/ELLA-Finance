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

class ReducibleSpendingGoalProviderTest {

    @Test
    @DisplayName("Picks top reducible category and avoids essentials")
    void picksReducibleCategory() {
        Person person = new Person();
        person.setId(UUID.randomUUID());
        person.setName("Test");

        YearMonth current = YearMonth.from(LocalDate.now());

        List<FinancialTransaction> txs = List.of(
        expense(person, current.atDay(2), "2000", "Aluguel"),
        expense(person, current.minusMonths(1).atDay(2), "2000", "Aluguel"),
        expense(person, current.minusMonths(2).atDay(2), "2000", "Aluguel"),
        expense(person, current.atDay(10), "500", "Restaurante"),
        expense(person, current.minusMonths(1).atDay(12), "450", "Restaurante"),
        expense(person, current.minusMonths(2).atDay(15), "550", "Restaurante")
        );

    ReducibleSpendingGoalProvider provider = new ReducibleSpendingGoalProvider();
    List<Goal> goals = provider.generateGoals(person, txs);

    assertEquals(1, goals.size());
    assertTrue(goals.getFirst().getTitle().toLowerCase().contains("reduzir"));
    assertTrue(goals.getFirst().getTitle().toLowerCase().contains("restaurante"));
    assertTrue(goals.getFirst().getTargetAmount().compareTo(BigDecimal.ZERO) > 0);
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
