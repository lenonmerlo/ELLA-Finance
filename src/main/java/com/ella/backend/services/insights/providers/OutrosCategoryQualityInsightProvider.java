package com.ella.backend.services.insights.providers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.ella.backend.dto.dashboard.InsightDTO;
import com.ella.backend.entities.FinancialTransaction;
import com.ella.backend.entities.Person;
import com.ella.backend.services.insights.InsightDataCache;
import com.ella.backend.services.insights.InsightUtils;

@Component
@Order(11)
public class OutrosCategoryQualityInsightProvider implements InsightProvider {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal OUTROS_THRESHOLD_PCT = BigDecimal.valueOf(20); // 20% do total do mês

    private static final Locale LOCALE_PT_BR = Locale.of("pt", "BR");

    private static final Pattern MULTISPACE = Pattern.compile("\\s+");
    private static final Pattern DIGITS = Pattern.compile("\\d+");

    private static final String CATEGORY_OUTROS = "Outros";

    private final InsightDataCache insightDataCache;

    public OutrosCategoryQualityInsightProvider(InsightDataCache insightDataCache) {
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

        BigDecimal totalExpenses = categoryTotals.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalExpenses.compareTo(BigDecimal.ZERO) <= 0) {
            return List.of();
        }

        // Se "Outros" for a top categoria, o TopCategoryInsightProvider já trata com mensagem acionável.
        String topCategory = categoryTotals.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(CATEGORY_OUTROS);

        if (isOutros(topCategory)) {
            return List.of();
        }

        BigDecimal outrosAmount = categoryTotals.getOrDefault(CATEGORY_OUTROS, BigDecimal.ZERO);
        if (outrosAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return List.of();
        }

        BigDecimal pct = outrosAmount.multiply(HUNDRED).divide(totalExpenses, 1, RoundingMode.HALF_UP);
        BigDecimal pct0 = pct.setScale(0, RoundingMode.HALF_UP);
        if (pct0.compareTo(OUTROS_THRESHOLD_PCT) < 0) {
            return List.of();
        }

        List<FinancialTransaction> outrosTxs = txs.stream()
                .filter(Objects::nonNull)
                .filter(InsightUtils::isExpense)
                .filter(t -> isOutros(InsightUtils.normalizeCategory(t.getCategory())))
                .toList();

        int count = outrosTxs.size();

        Map<String, BigDecimal> byKey = outrosTxs.stream()
                .collect(Collectors.groupingBy(
                        t -> normalizeDescriptionKey(t.getDescription()),
                        Collectors.reducing(BigDecimal.ZERO, InsightUtils::safeAmount, BigDecimal::add)
                ));

        String examplesText = topExamplesText(byKey, 3);

        String message = String.format(
                "Uma parte relevante dos seus gastos caiu em 'Outros': %s (%.0f%% do total) em %d lançamentos. "
                        + "Isso pode indicar que a categorização está incompleta. Sugestão: revise 'Outros' em Transações e recategorize. %s",
                InsightUtils.formatCurrency(outrosAmount),
                pct0,
                count,
                examplesText
        ).trim();

        return List.of(InsightDTO.builder()
                .type("warning")
                .category("Gastos")
                .message(message)
                .build());
    }

    private boolean isOutros(String category) {
        if (category == null) {
            return false;
        }
        String c = category.trim();
        return CATEGORY_OUTROS.equalsIgnoreCase(c) || "Other".equalsIgnoreCase(c);
    }

    private String topExamplesText(Map<String, BigDecimal> byKey, int maxItems) {
        if (byKey == null || byKey.isEmpty() || maxItems <= 0) {
            return "";
        }

        List<Map.Entry<String, BigDecimal>> sorted = byKey.entrySet().stream()
                .filter(e -> e.getKey() != null && !e.getKey().isBlank())
                .filter(e -> e.getValue() != null && e.getValue().compareTo(BigDecimal.ZERO) > 0)
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .limit(maxItems)
                .toList();

        if (sorted.isEmpty()) {
            return "";
        }

        List<String> items = new ArrayList<>(sorted.size());
        for (Map.Entry<String, BigDecimal> e : sorted) {
            items.add(String.format("%s (%s)", displayNameForKey(e.getKey()), InsightUtils.formatCurrency(e.getValue())));
        }

        return "Principais descrições em 'Outros': " + String.join(", ", items) + ".";
    }

    private String displayNameForKey(String key) {
        if (key == null) {
            return "";
        }
        String s = key.trim();
        if (s.isBlank()) {
            return "";
        }
        return s.toUpperCase(LOCALE_PT_BR);
    }

    private String normalizeDescriptionKey(String description) {
        if (description == null) {
            return "";
        }
        String s = description.toLowerCase(LOCALE_PT_BR).trim();
        s = DIGITS.matcher(s).replaceAll("");
        s = s.replaceAll("[^a-zà-ú0-9 ]", " ");
        s = MULTISPACE.matcher(s).replaceAll(" ").trim();

        if (!s.isBlank()) {
            java.util.Set<String> stop = java.util.Set.of("com", "br", "www");
            s = java.util.List.of(s.split(" ")).stream()
                    .filter(t -> !t.isBlank())
                    .filter(t -> !stop.contains(t))
                    .collect(Collectors.joining(" "))
                    .trim();
        }

        return s;
    }
}
