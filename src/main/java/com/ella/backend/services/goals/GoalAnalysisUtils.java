package com.ella.backend.services.goals;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.ella.backend.entities.FinancialTransaction;
import com.ella.backend.enums.TransactionType;

public final class GoalAnalysisUtils {

    public static final Locale LOCALE_PT_BR = Locale.of("pt", "BR");

    private static final Pattern MULTISPACE = Pattern.compile("\\s+");
    private static final Pattern DIGITS = Pattern.compile("\\d+");

    private GoalAnalysisUtils() {
    }

    public static boolean isExpense(FinancialTransaction tx) {
        return tx != null && tx.getType() == TransactionType.EXPENSE;
    }

    public static boolean isIncome(FinancialTransaction tx) {
        return tx != null && tx.getType() == TransactionType.INCOME;
    }

    public static BigDecimal safeAmount(FinancialTransaction tx) {
        if (tx == null || tx.getAmount() == null) {
            return BigDecimal.ZERO;
        }
        return tx.getAmount();
    }

    public static String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return "Outros";
        }
        return category.trim();
    }

    public static boolean isNonReducibleCategory(String category) {
        String c = normalizeCategory(category).toLowerCase(LOCALE_PT_BR);
        if (c.isBlank()) {
            return false;
        }
        return containsAny(c, Set.of(
                "saúde", "saude", "educação", "educacao", "moradia", "aluguel", "hipoteca", "condomínio", "condominio",
                "utilidades", "água", "agua", "luz", "energia", "internet", "gás", "gas",
                "transporte", "combustível", "combustivel", "gasolina", "ônibus", "onibus", "metrô", "metro"
        ));
    }

    public static boolean isReducibleCategory(String category) {
        return !isNonReducibleCategory(category);
    }

    private static boolean containsAny(String haystack, Set<String> needles) {
        for (String n : needles) {
            if (haystack.contains(n)) {
                return true;
            }
        }
        return false;
    }

    public static List<YearMonth> distinctMonths(List<FinancialTransaction> txs) {
        if (txs == null || txs.isEmpty()) {
            return List.of();
        }
        return txs.stream()
                .filter(Objects::nonNull)
                .map(FinancialTransaction::getTransactionDate)
                .filter(Objects::nonNull)
                .map(YearMonth::from)
                .distinct()
                .sorted()
                .toList();
    }

    public static Map<String, BigDecimal> totalExpensesByCategory(List<FinancialTransaction> txs) {
        if (txs == null || txs.isEmpty()) {
            return Map.of();
        }
        Map<String, BigDecimal> totals = new HashMap<>();
        for (FinancialTransaction tx : txs) {
            if (!isExpense(tx)) {
                continue;
            }
            BigDecimal amount = safeAmount(tx);
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            String category = normalizeCategory(tx.getCategory());
            totals.put(category, totals.getOrDefault(category, BigDecimal.ZERO).add(amount));
        }
        return totals;
    }

    public static Map<YearMonth, BigDecimal> monthlyExpensesForCategory(List<FinancialTransaction> txs, String category) {
        if (txs == null || txs.isEmpty()) {
            return Map.of();
        }
        String wanted = normalizeCategory(category);
        Map<YearMonth, BigDecimal> out = new HashMap<>();
        for (FinancialTransaction tx : txs) {
            if (!isExpense(tx)) {
                continue;
            }
            if (!wanted.equals(normalizeCategory(tx.getCategory()))) {
                continue;
            }
            LocalDate date = tx.getTransactionDate();
            if (date == null) {
                continue;
            }
            BigDecimal amount = safeAmount(tx);
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            YearMonth ym = YearMonth.from(date);
            out.put(ym, out.getOrDefault(ym, BigDecimal.ZERO).add(amount));
        }
        return out;
    }

    public static BigDecimal averagePerMonth(BigDecimal total, int months) {
        if (total == null || months <= 0) {
            return BigDecimal.ZERO;
        }
        return total.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
    }

    public static String formatCurrency(BigDecimal amount) {
        NumberFormat nf = NumberFormat.getCurrencyInstance(LOCALE_PT_BR);
        BigDecimal safe = amount != null ? amount : BigDecimal.ZERO;
        return nf.format(safe);
    }

    public static double mean(List<BigDecimal> values) {
        if (values == null || values.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        int n = 0;
        for (BigDecimal v : values) {
            if (v == null) {
                continue;
            }
            sum += v.doubleValue();
            n++;
        }
        return n == 0 ? 0.0 : sum / n;
    }

    public static double stdDevSample(List<BigDecimal> values, double mean) {
        if (values == null) {
            return 0.0;
        }
        List<BigDecimal> cleaned = values.stream().filter(Objects::nonNull).toList();
        int n = cleaned.size();
        if (n < 2) {
            return 0.0;
        }
        double sumSq = 0.0;
        for (BigDecimal v : cleaned) {
            double d = v.doubleValue() - mean;
            sumSq += d * d;
        }
        return Math.sqrt(sumSq / (n - 1));
    }

    public static String normalizeDescriptionKey(String description) {
        if (description == null) {
            return "";
        }
        String s = description.toLowerCase(LOCALE_PT_BR).trim();
        s = DIGITS.matcher(s).replaceAll("");
        s = s.replaceAll("[^a-zà-ú0-9 ]", " ");
        s = MULTISPACE.matcher(s).replaceAll(" ").trim();
        if (!s.isBlank()) {
            Set<String> stop = Set.of("com", "br", "www");
            s = List.of(s.split(" ")).stream()
                    .filter(t -> !t.isBlank())
                    .filter(t -> !stop.contains(t))
                    .collect(Collectors.joining(" "))
                    .trim();
        }
        return s;
    }

    public static List<String> topKeysByAmount(Map<String, BigDecimal> byKey, int limit) {
        if (byKey == null || byKey.isEmpty()) {
            return List.of();
        }
        return byKey.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue(Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
    }

    public static Map<String, BigDecimal> estimatedMonthlyCostBySubscriptionKey(
            Map<String, Map<YearMonth, BigDecimal>> monthlyByKey,
            List<YearMonth> requiredMonths
    ) {
        if (monthlyByKey == null || monthlyByKey.isEmpty() || requiredMonths == null || requiredMonths.isEmpty()) {
            return Map.of();
        }

        Map<String, BigDecimal> out = new LinkedHashMap<>();
        Set<YearMonth> required = Set.copyOf(requiredMonths);

        for (Map.Entry<String, Map<YearMonth, BigDecimal>> entry : monthlyByKey.entrySet()) {
            String key = entry.getKey();
            Map<YearMonth, BigDecimal> perMonth = entry.getValue();
            if (key == null || key.isBlank() || perMonth == null) {
                continue;
            }
            if (!perMonth.keySet().containsAll(required)) {
                continue;
            }
            List<BigDecimal> amounts = requiredMonths.stream().map(ym -> perMonth.getOrDefault(ym, BigDecimal.ZERO)).toList();
            double mean = mean(amounts);
            double std = stdDevSample(amounts, mean);
            if (mean <= 0.0) {
                continue;
            }
            if (std > Math.max(5.0, mean * 0.08)) {
                continue;
            }
            out.put(key, BigDecimal.valueOf(mean).setScale(2, RoundingMode.HALF_UP));
        }

        return out;
    }

    public static List<YearMonth> trailingMonthsInclusive(YearMonth current, int count) {
        if (count <= 0) {
            return List.of();
        }
        List<YearMonth> out = new ArrayList<>(count);
        YearMonth start = current.minusMonths(count - 1L);
        for (int i = 0; i < count; i++) {
            out.add(start.plusMonths(i));
        }
        return out;
    }
}
