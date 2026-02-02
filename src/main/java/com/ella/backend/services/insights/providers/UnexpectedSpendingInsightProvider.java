package com.ella.backend.services.insights.providers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
@Order(30)
public class UnexpectedSpendingInsightProvider implements InsightProvider {

    private final InsightDataCache insightDataCache;

    public UnexpectedSpendingInsightProvider(InsightDataCache insightDataCache) {
        this.insightDataCache = insightDataCache;
    }

    @Override
    public List<InsightDTO> generate(Person person, int year, int month) {
        YearMonth currentYm = YearMonth.of(year, month);

        List<FinancialTransaction> currentTx = insightDataCache.getTransactionsForMonth(person, currentYm);
        if (currentTx == null || currentTx.isEmpty()) {
            return List.of();
        }

        Map<String, BigDecimal> currentTotals = totalsByCategory(currentTx);
        if (currentTotals.isEmpty()) {
            return List.of();
        }

        List<YearMonth> baselineMonths = InsightDataCache.previousMonths(currentYm, 3);
        Map<YearMonth, Map<String, BigDecimal>> baselineTotalsByMonth = new HashMap<>();
        for (YearMonth ym : baselineMonths) {
            List<FinancialTransaction> txs = insightDataCache.getTransactionsForMonth(person, ym);
            baselineTotalsByMonth.put(ym, totalsByCategory(txs));
        }

        List<UnexpectedCategory> candidates = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : currentTotals.entrySet()) {
            String category = entry.getKey();
            BigDecimal currentTotal = entry.getValue();

            if (currentTotal == null || currentTotal.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            // Baseline: últimos 3 meses (ignorando meses sem gasto nessa categoria)
            List<BigDecimal> baseline = baselineMonths.stream()
                    .map(m -> baselineTotalsByMonth.getOrDefault(m, Map.of()).getOrDefault(category, BigDecimal.ZERO))
                    .filter(v -> v.compareTo(BigDecimal.ZERO) > 0)
                    .toList();

            if (baseline.size() < 2) {
                continue;
            }

            double mean = InsightUtils.mean(baseline);
            double std = InsightUtils.stdDevSample(baseline, mean);

            if (mean <= 0.0) {
                continue;
            }

            BigDecimal meanBd = BigDecimal.valueOf(mean).setScale(2, RoundingMode.HALF_UP);

            // Threshold: média + 2*desvio padrão; se std=0, usa multiplicador para evitar falsos positivos.
            double threshold = std > 0.0 ? mean + 2.0 * std : mean * 1.75;

            BigDecimal thresholdBd = BigDecimal.valueOf(threshold).setScale(2, RoundingMode.HALF_UP);

            // Ruído: exige crescimento mínimo absoluto e relativo
            BigDecimal minAbsoluteIncrease = BigDecimal.valueOf(50);
            BigDecimal minRelativeMultiplier = BigDecimal.valueOf(1.35);

            boolean aboveThreshold = currentTotal.compareTo(thresholdBd) > 0;
            boolean aboveRelative = currentTotal.compareTo(meanBd.multiply(minRelativeMultiplier)) > 0;
            boolean aboveAbsolute = currentTotal.subtract(meanBd).compareTo(minAbsoluteIncrease) > 0;

            if (!aboveThreshold || !aboveRelative || !aboveAbsolute) {
                continue;
            }

            candidates.add(new UnexpectedCategory(category, currentTotal, meanBd));
        }

        candidates.sort(Comparator.comparing(UnexpectedCategory::excessAmount).reversed());

        int maxToAdd = 3;
        return candidates.stream()
                .limit(maxToAdd)
                .map(c -> {
                    boolean bigSpike = c.current().compareTo(c.mean().multiply(BigDecimal.valueOf(2))) > 0;
                    String type = bigSpike ? "alert" : "warning";

                    return InsightDTO.builder()
                            .type(type)
                            .message(String.format(
                                    "Você gastou mais com %s este mês: %s. Sua média recente é %s/mês.",
                                    c.category(),
                                    InsightUtils.formatCurrency(c.current()),
                                    InsightUtils.formatCurrency(c.mean())
                            ))
                            .category("Anomalia")
                            .build();
                })
                .collect(Collectors.toList());
    }

    private Map<String, BigDecimal> totalsByCategory(List<FinancialTransaction> txs) {
        if (txs == null || txs.isEmpty()) {
            return Map.of();
        }

        return txs.stream()
                .filter(Objects::nonNull)
                .filter(InsightUtils::isExpense)
                .collect(Collectors.groupingBy(
                        t -> InsightUtils.normalizeCategory(t.getCategory()),
                        Collectors.reducing(BigDecimal.ZERO, InsightUtils::safeAmount, BigDecimal::add)
                ));
    }

    private record UnexpectedCategory(String category, BigDecimal current, BigDecimal mean) {
        BigDecimal excessAmount() {
            return current.subtract(mean);
        }
    }
}
