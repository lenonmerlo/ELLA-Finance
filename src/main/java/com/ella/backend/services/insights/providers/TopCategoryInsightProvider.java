package com.ella.backend.services.insights.providers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.ella.backend.dto.dashboard.InsightDTO;
import com.ella.backend.entities.FinancialTransaction;
import com.ella.backend.entities.Person;
import com.ella.backend.services.insights.InsightDataCache;
import com.ella.backend.services.insights.InsightUtils;

@Component
@Order(10)
public class TopCategoryInsightProvider implements InsightProvider {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final InsightDataCache insightDataCache;

    public TopCategoryInsightProvider(InsightDataCache insightDataCache) {
        this.insightDataCache = insightDataCache;
    }

    @Override
    public List<InsightDTO> generate(Person person, int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        List<FinancialTransaction> txs = insightDataCache.getTransactionsForMonth(person, ym);
        if (txs == null || txs.isEmpty()) {
            return List.of();
        }

        Map<String, BigDecimal> categoryTotals = txs.stream()
                .filter(Objects::nonNull)
                .filter(InsightUtils::isExpense)
                .collect(Collectors.groupingBy(
                        t -> InsightUtils.normalizeCategory(t.getCategory()),
                        Collectors.reducing(BigDecimal.ZERO, InsightUtils::safeAmount, BigDecimal::add)
                ));

        if (categoryTotals.isEmpty()) {
            return List.of();
        }

        String topCategory = categoryTotals.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("Outros");

        BigDecimal topAmount = categoryTotals.getOrDefault(topCategory, BigDecimal.ZERO);
        BigDecimal totalExpenses = categoryTotals.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal percentage = totalExpenses.compareTo(BigDecimal.ZERO) > 0
                ? topAmount.multiply(HUNDRED).divide(totalExpenses, 1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return List.of(InsightDTO.builder()
                .type("info")
                .message(String.format(
                        "Você gastou %s em %s (%.0f%% do total)",
                        InsightUtils.formatCurrency(topAmount),
                        topCategory,
                        percentage
                ))
                .category("Gastos")
                .build());
    }
}
