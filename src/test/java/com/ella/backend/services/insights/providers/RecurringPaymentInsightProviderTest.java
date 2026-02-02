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
import com.ella.backend.services.insights.InsightDataCache;

@ExtendWith(MockitoExtension.class)
class RecurringPaymentInsightProviderTest {

    @Mock
    private FinancialTransactionRepository financialTransactionRepository;

    @Test
    @DisplayName("Detects recurring payments across 3 consecutive months")
    void detectsRecurringPayments() {
        Person person = new Person();
        person.setId(UUID.randomUUID());
        person.setName("Test");

        YearMonth current = YearMonth.of(2026, 2);

        Map<YearMonth, List<FinancialTransaction>> byMonth = new HashMap<>();
        byMonth.put(current, List.of(
                tx(person, LocalDate.of(2026, 2, 7), "Streaming", "Netflix", "39.90")
        ));
        byMonth.put(current.minusMonths(1), List.of(
                tx(person, LocalDate.of(2026, 1, 6), "Streaming", "NETFLIX.COM", "39.90")
        ));
        byMonth.put(current.minusMonths(2), List.of(
                tx(person, LocalDate.of(2025, 12, 8), "Streaming", "Netflix", "39.90")
        ));

        when(financialTransactionRepository.findByPersonAndTransactionDateBetween(eq(person), any(LocalDate.class), any(LocalDate.class)))
                .thenAnswer(invocation -> {
                    LocalDate start = invocation.getArgument(1);
                    return byMonth.getOrDefault(YearMonth.from(start), List.of());
                });

        InsightDataCache cache = new InsightDataCache(financialTransactionRepository);
        RecurringPaymentInsightProvider provider = new RecurringPaymentInsightProvider(cache);

        List<InsightDTO> insights = provider.generate(person, current.getYear(), current.getMonthValue());

        assertEquals(1, insights.size());
        assertEquals("Assinaturas", insights.getFirst().getCategory());
        String msg = insights.getFirst().getMessage().toLowerCase();
        assertTrue(msg.contains("recorrentes"));
        assertTrue(msg.contains("netflix"));
    }

    @Test
    @DisplayName("Does not consider PIX/transfer-like descriptions as subscriptions")
    void ignoresPixTransfers() {
        Person person = new Person();
        person.setId(UUID.randomUUID());
        person.setName("Test");

        YearMonth current = YearMonth.of(2026, 2);

        Map<YearMonth, List<FinancialTransaction>> byMonth = new HashMap<>();
        byMonth.put(current, List.of(tx(person, LocalDate.of(2026, 2, 7), "Outros", "PIX JOAO", "39.90")));
        byMonth.put(current.minusMonths(1), List.of(tx(person, LocalDate.of(2026, 1, 7), "Outros", "PIX JOAO", "39.90")));
        byMonth.put(current.minusMonths(2), List.of(tx(person, LocalDate.of(2025, 12, 7), "Outros", "PIX JOAO", "39.90")));

        when(financialTransactionRepository.findByPersonAndTransactionDateBetween(eq(person), any(LocalDate.class), any(LocalDate.class)))
                .thenAnswer(invocation -> {
                    LocalDate start = invocation.getArgument(1);
                    return byMonth.getOrDefault(YearMonth.from(start), List.of());
                });

        InsightDataCache cache = new InsightDataCache(financialTransactionRepository);
        RecurringPaymentInsightProvider provider = new RecurringPaymentInsightProvider(cache);

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
