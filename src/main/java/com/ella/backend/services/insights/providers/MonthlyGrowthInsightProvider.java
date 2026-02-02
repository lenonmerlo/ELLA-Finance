package com.ella.backend.services.insights.providers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.ella.backend.dto.dashboard.InsightDTO;
import com.ella.backend.entities.FinancialTransaction;
import com.ella.backend.entities.Person;
import com.ella.backend.services.insights.InsightDataCache;
import com.ella.backend.services.insights.InsightUtils;

@Component
@Order(20)
public class MonthlyGrowthInsightProvider implements InsightProvider {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final InsightDataCache insightDataCache;

    public MonthlyGrowthInsightProvider(InsightDataCache insightDataCache) {
        this.insightDataCache = insightDataCache;
    }

    @Override
    public List<InsightDTO> generate(Person person, int year, int month) {
        YearMonth current = YearMonth.of(year, month);
        List<FinancialTransaction> currentTx = insightDataCache.getTransactionsForMonth(person, current);
        if (currentTx == null || currentTx.isEmpty()) {
            return List.of();
        }

        BigDecimal currentTotal = currentTx.stream()
                .filter(Objects::nonNull)
                .filter(InsightUtils::isExpense)
                .map(InsightUtils::safeAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        YearMonth previous = InsightDataCache.previousMonth(current);
        List<FinancialTransaction> previousTx = insightDataCache.getTransactionsForMonth(person, previous);

        BigDecimal previousTotal = previousTx.stream()
                .filter(Objects::nonNull)
                .filter(InsightUtils::isExpense)
                .map(InsightUtils::safeAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (previousTotal.compareTo(BigDecimal.ZERO) <= 0) {
            return List.of();
        }

        BigDecimal variation = currentTotal
                .subtract(previousTotal)
                .divide(previousTotal, 2, RoundingMode.HALF_UP)
                .multiply(HUNDRED);

        if (variation.abs().compareTo(BigDecimal.valueOf(10)) <= 0) {
            return List.of();
        }

        boolean increased = variation.compareTo(BigDecimal.ZERO) > 0;
        String direction = increased ? "aumentaram" : "diminuíram";
        String type = increased ? "warning" : "success";

        return List.of(InsightDTO.builder()
                .type(type)
                .message(String.format(
                        "Seus gastos %s %.0f%% em relação ao mês anterior",
                        direction,
                        variation.abs()
                ))
                .category("Tendência")
                .build());
    }
}
