package com.ella.backend.services.insights;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.ella.backend.entities.FinancialTransaction;
import com.ella.backend.enums.TransactionType;

public final class InsightUtils {

    public static final Locale LOCALE_PT_BR = Locale.of("pt", "BR");

    private InsightUtils() {
    }

    public static BigDecimal safeAmount(FinancialTransaction transaction) {
        if (transaction == null || transaction.getAmount() == null) {
            return BigDecimal.ZERO;
        }
        return transaction.getAmount();
    }

    public static boolean isExpense(FinancialTransaction transaction) {
        return transaction != null && transaction.getType() == TransactionType.EXPENSE;
    }

    public static String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return "Outros";
        }
        return category.trim();
    }

    public static String formatCurrency(BigDecimal amount) {
        NumberFormat nf = NumberFormat.getCurrencyInstance(LOCALE_PT_BR);
        BigDecimal safe = amount != null ? amount : BigDecimal.ZERO;
        return nf.format(safe);
    }

    public static List<BigDecimal> toAmounts(List<FinancialTransaction> txs) {
        if (txs == null || txs.isEmpty()) {
            return List.of();
        }
        List<BigDecimal> out = new ArrayList<>(txs.size());
        for (FinancialTransaction tx : txs) {
            if (tx == null) {
                continue;
            }
            out.add(safeAmount(tx));
        }
        return out;
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
}
