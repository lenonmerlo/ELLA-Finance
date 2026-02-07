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
class TopCategoryInsightProviderTest {

    @Mock
    private InsightDataCache insightDataCache;

    @Test
    @DisplayName("When top category is 'Outros', returns actionable message with examples")
    void outrosIsActionable() {
        Person p = new Person();
        p.setName("Test");

        int year = 2026;
        int month = 2;
        YearMonth ym = YearMonth.of(year, month);

        List<FinancialTransaction> txs = List.of(
                expense(p, "UBER TRIP 1234", "Outros", "200.00", ym.atDay(2)),
                expense(p, "IFOOD 999", "Outros", "180.00", ym.atDay(3)),
                expense(p, "SUPERMERCADO", "Alimentação", "350.00", ym.atDay(5)),
                expense(p, "POSTO", "Transporte", "270.00", ym.atDay(8))
        );

        when(insightDataCache.getTransactionsForMonth(p, ym)).thenReturn(txs);

        TopCategoryInsightProvider provider = new TopCategoryInsightProvider(insightDataCache);
        List<InsightDTO> out = provider.generate(p, year, month);

        assertNotNull(out);
        assertEquals(1, out.size());

        InsightDTO i = out.getFirst();
        assertEquals("Gastos", i.getCategory());
        assertNotNull(i.getMessage());

        String msg = i.getMessage();
        assertTrue(msg.contains("'Outros'"));
        assertTrue(msg.toLowerCase().contains("sugest"));
        assertTrue(msg.toLowerCase().contains("transa"));
        assertTrue(msg.contains("UBER"));
        assertTrue(msg.contains("IFOOD"));

        // Outros = 380 de 1000 => 38%
        assertTrue(msg.contains("38"));
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
