package com.ella.backend.services.insights.providers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

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
import com.ella.backend.services.insights.InsightDataCache;

@ExtendWith(MockitoExtension.class)
class OutrosCategoryQualityInsightProviderTest {

    @Mock
    private InsightDataCache insightDataCache;

    @Test
    @DisplayName("Generates warning when 'Outros' >= 20% even if it's not the top category")
    void generatesWhenOutrosIsLargeButNotTop() {
        Person p = new Person();
        p.setName("Test");

        int year = 2026;
        int month = 2;
        YearMonth ym = YearMonth.of(year, month);

        // Total = 1200, Outros = 260 => 22%
        List<FinancialTransaction> txs = List.of(
                expense(p, "SUPERMERCADO", "Alimentação", "600.00", ym.atDay(2)),
                expense(p, "RESTAURANTE", "Alimentação", "340.00", ym.atDay(3)),
                expense(p, "UBER TRIP 1234", "Outros", "140.00", ym.atDay(5)),
                expense(p, "IFOOD 999", "Outros", "120.00", ym.atDay(9))
        );

        when(insightDataCache.getTransactionsForMonth(p, ym)).thenReturn(txs);

        OutrosCategoryQualityInsightProvider provider = new OutrosCategoryQualityInsightProvider(insightDataCache);
        List<InsightDTO> out = provider.generate(p, year, month);

        assertNotNull(out);
        assertEquals(1, out.size());

        InsightDTO i = out.getFirst();
        assertEquals("warning", i.getType());
        assertEquals("Gastos", i.getCategory());
        assertTrue(i.getMessage().contains("'Outros'"));
        assertTrue(i.getMessage().contains("UBER"));
        assertTrue(i.getMessage().contains("IFOOD"));
        assertTrue(i.getMessage().contains("22"));
    }

    @Test
    @DisplayName("Does not generate when top category is 'Outros' (handled by TopCategoryInsightProvider)")
    void doesNotGenerateWhenOutrosIsTop() {
        Person p = new Person();
        p.setName("Test");

        int year = 2026;
        int month = 2;
        YearMonth ym = YearMonth.of(year, month);

        List<FinancialTransaction> txs = List.of(
                expense(p, "UBER TRIP 1234", "Outros", "600.00", ym.atDay(2)),
                expense(p, "IFOOD 999", "Outros", "300.00", ym.atDay(3)),
                expense(p, "SUPERMERCADO", "Alimentação", "200.00", ym.atDay(5))
        );

        when(insightDataCache.getTransactionsForMonth(p, ym)).thenReturn(txs);

        OutrosCategoryQualityInsightProvider provider = new OutrosCategoryQualityInsightProvider(insightDataCache);
        List<InsightDTO> out = provider.generate(p, year, month);

        assertNotNull(out);
        assertEquals(0, out.size());
    }

    private static FinancialTransaction expense(
            Person p,
            String description,
            String category,
            String amount,
            LocalDate date
    ) {
        return FinancialTransaction.builder()
                .person(p)
                .description(description)
                .category(category)
                .amount(new BigDecimal(amount))
                .type(TransactionType.EXPENSE)
                .transactionDate(date)
                .status(TransactionStatus.PAID)
                .build();
    }
}
