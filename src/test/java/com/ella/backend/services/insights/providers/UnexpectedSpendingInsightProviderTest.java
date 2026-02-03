package com.ella.backend.services.insights.providers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
class UnexpectedSpendingInsightProviderTest {

    @Mock
    private FinancialTransactionRepository financialTransactionRepository;

        @Mock
        private CashflowTransactionsService cashflowTransactionsService;

    @Test
    @DisplayName("Generates insight when current month spend is far above 3-month baseline")
    void generatesUnexpectedSpendingInsight() {
        Person person = new Person();
        person.setId(UUID.randomUUID());
        person.setName("Test");

        YearMonth current = YearMonth.of(2026, 2);

        Map<YearMonth, List<FinancialTransaction>> byMonth = new HashMap<>();
        // Baseline: 3 meses anteriores (valores estáveis)
        byMonth.put(current.minusMonths(1), List.of(
                tx(person, LocalDate.of(2026, 1, 10), "Restaurantes", "Restaurante A", "100.00"),
                tx(person, LocalDate.of(2026, 1, 15), "Restaurantes", "Restaurante B", "10.00")
        ));
        byMonth.put(current.minusMonths(2), List.of(
                tx(person, LocalDate.of(2025, 12, 11), "Restaurantes", "Restaurante A", "90.00"),
                tx(person, LocalDate.of(2025, 12, 20), "Restaurantes", "Restaurante B", "20.00")
        ));
        byMonth.put(current.minusMonths(3), List.of(
                tx(person, LocalDate.of(2025, 11, 9), "Restaurantes", "Restaurante A", "110.00")
        ));

        // Atual: spike grande
        byMonth.put(current, List.of(
                tx(person, LocalDate.of(2026, 2, 5), "Restaurantes", "Restaurante A", "250.00")
        ));

        when(financialTransactionRepository.findByPersonAndTransactionDateBetween(eq(person), any(LocalDate.class), any(LocalDate.class)))
                .thenAnswer(invocation -> {
                    LocalDate start = invocation.getArgument(1);
                    return byMonth.getOrDefault(YearMonth.from(start), List.of());
                });

        InsightDataCache cache = new InsightDataCache(financialTransactionRepository, cashflowTransactionsService);
        UnexpectedSpendingInsightProvider provider = new UnexpectedSpendingInsightProvider(cache);

        List<InsightDTO> insights = provider.generate(person, current.getYear(), current.getMonthValue());

        assertEquals(1, insights.size());
        assertEquals("Anomalia", insights.getFirst().getCategory());
        assertTrue(insights.getFirst().getMessage().toLowerCase().contains("restaurantes"));
    }

    @Test
    @DisplayName("Does not generate insight if baseline is insufficient")
    void doesNotGenerateWithInsufficientBaseline() {
        Person person = new Person();
        person.setId(UUID.randomUUID());
        person.setName("Test");

        YearMonth current = YearMonth.of(2026, 2);

        Map<YearMonth, List<FinancialTransaction>> byMonth = Map.of(
                current, List.of(tx(person, LocalDate.of(2026, 2, 5), "Restaurantes", "Restaurante A", "250.00")),
                current.minusMonths(1), List.of(tx(person, LocalDate.of(2026, 1, 10), "Restaurantes", "Restaurante A", "100.00"))
        );

        when(financialTransactionRepository.findByPersonAndTransactionDateBetween(eq(person), any(LocalDate.class), any(LocalDate.class)))
                .thenAnswer(invocation -> {
                    LocalDate start = invocation.getArgument(1);
                    return byMonth.getOrDefault(YearMonth.from(start), List.of());
                });

        InsightDataCache cache = new InsightDataCache(financialTransactionRepository, cashflowTransactionsService);
        UnexpectedSpendingInsightProvider provider = new UnexpectedSpendingInsightProvider(cache);

        List<InsightDTO> insights = provider.generate(person, current.getYear(), current.getMonthValue());
        assertTrue(insights.isEmpty());
    }

    private static FinancialTransaction tx(
            Person person,
            LocalDate date,
            String category,
            String description,
            String amount
    ) {
        return FinancialTransaction.builder()
                .person(person)
                .transactionDate(date)
                .purchaseDate(date)
                .category(category)
                .description(description)
                .amount(new BigDecimal(amount))
                .type(TransactionType.EXPENSE)
                .status(TransactionStatus.PAID)
                .build();
    }
}
