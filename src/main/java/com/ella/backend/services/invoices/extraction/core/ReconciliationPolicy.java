package com.ella.backend.services.invoices.extraction.core;

import java.math.BigDecimal;
import java.util.List;

import com.ella.backend.services.invoices.parsers.TransactionData;

public final class ReconciliationPolicy {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ReconciliationPolicy.class);

    private static final BigDecimal MISSING_TX_RATIO_THRESHOLD = new BigDecimal("0.96");

    private ReconciliationPolicy() {
    }

    public static boolean shouldRetryWithOcrForQuality(List<TransactionData> transactions) {
        if (transactions == null || transactions.isEmpty()) return false;

        int total = 0;
        int garbled = 0;
        int missingDate = 0;

        for (TransactionData tx : transactions) {
            if (tx == null) continue;
            total++;
            if (tx.date == null) missingDate++;
            if (isLikelyGarbledMerchant(tx.description)) garbled++;
        }

        if (total == 0) return false;

        boolean anyGarbledDescs = garbled >= 1;
        boolean manyMissingDates = missingDate >= Math.max(2, (int) Math.ceil(total * 0.5));
        boolean trigger = manyMissingDates || anyGarbledDescs;

        if (trigger) {
            StringBuilder sample = new StringBuilder();
            int shown = 0;
            for (TransactionData tx : transactions) {
                if (tx == null) continue;
                if (!isLikelyGarbledMerchant(tx.description) && tx.date != null) continue;
                if (shown >= 3) break;
                String desc = tx.description == null ? "" : tx.description;
                if (desc.length() > 60) desc = desc.substring(0, 60) + "...";
                sample.append('[').append(tx.date).append(']').append(desc).append(' ');
                shown++;
            }
            LOG.info("[OCR] Quality trigger: total={} garbled={} missingDate={} samples={}", total, garbled, missingDate, sample.toString().trim());
        }

        return trigger;
    }

    public static boolean shouldRetryDueToMissingTransactions(List<TransactionData> transactions, String text) {
        if (transactions == null || transactions.isEmpty()) return false;

        BigDecimal expectedTotal = TotalResolver.extractInvoiceExpectedTotal(text);
        if (expectedTotal == null || expectedTotal.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }

        BigDecimal totalExtracted = sumExpenseAmounts(transactions);
        if (totalExtracted.compareTo(BigDecimal.ZERO) <= 0) return false;

        BigDecimal threshold = expectedTotal.multiply(MISSING_TX_RATIO_THRESHOLD);
        boolean trigger = totalExtracted.compareTo(threshold) < 0;

        BigDecimal pct = totalExtracted.multiply(BigDecimal.valueOf(100))
                .divide(expectedTotal, 2, java.math.RoundingMode.HALF_UP);
        LOG.info(
                "[InvoiceUpload][OCR] Total check: txCount={} extracted={} expected={} threshold={} ({}% of expected) trigger={}",
                transactions.size(), totalExtracted, expectedTotal, threshold, pct, trigger);

        return trigger;
    }

    public static boolean isOcrResultBetterForMissingTransactions(
            List<TransactionData> ocrTransactions,
            List<TransactionData> originalTransactions,
            BigDecimal expectedTotal
    ) {
        if (expectedTotal == null || expectedTotal.compareTo(BigDecimal.ZERO) <= 0) return false;

        BigDecimal origTotal = sumExpenseAmounts(originalTransactions);
        BigDecimal ocrTotal = sumExpenseAmounts(ocrTransactions);

        BigDecimal origDiff = expectedTotal.subtract(origTotal).abs();
        BigDecimal ocrDiff = expectedTotal.subtract(ocrTotal).abs();

        int origCount = originalTransactions == null ? 0 : originalTransactions.size();
        int ocrCount = ocrTransactions == null ? 0 : ocrTransactions.size();

        boolean better = false;

        if (ocrCount >= origCount && ocrDiff.compareTo(origDiff) < 0) {
            better = true;
        }

        if (!better && ocrCount > origCount && ocrDiff.compareTo(origDiff) <= 0) {
            better = true;
        }

        LOG.info("[InvoiceUpload][OCR] OCR retry evaluated: txCount {} -> {} | extracted {} -> {} | diffToExpected {} -> {} | accepted={} ",
                origCount, ocrCount, origTotal, ocrTotal, origDiff, ocrDiff, better);

        return better;
    }

    public static boolean isOcrResultBetter(List<TransactionData> ocrResult, List<TransactionData> original) {
        int ocrScore = qualityScore(ocrResult);
        int origScore = qualityScore(original);
        return ocrScore > origScore;
    }

    public static boolean isLikelyGarbledMerchant(String description) {
        if (description == null) return false;
        String d = description.trim();
        if (d.isEmpty()) return false;

        String upper = d.toUpperCase(java.util.Locale.ROOT);
        if (upper.contains("UBER") || upper.contains("IFOOD") || upper.contains("PAGAMENTO") || upper.contains("ANUIDADE")) {
            return false;
        }

        int letters = 0;
        int digits = 0;
        int vowels = 0;
        int longAlnumTokens = 0;
        int mixedLetterDigitTokens = 0;

        for (String token : d.split("\\s+")) {
            String t = token.replaceAll("[^A-Za-z0-9]", "");
            if (t.length() < 8) continue;

            boolean hasLetter = t.matches(".*[A-Za-z].*");
            boolean hasDigit = t.matches(".*\\d.*");
            if (t.length() >= 10 && t.matches("[A-Za-z0-9]+")) {
                longAlnumTokens++;
            }
            if (t.length() >= 10 && hasLetter && hasDigit && countDigits(t) >= 2) {
                mixedLetterDigitTokens++;
            }
        }

        for (int i = 0; i < upper.length(); i++) {
            char c = upper.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                letters++;
                if (c == 'A' || c == 'E' || c == 'I' || c == 'O' || c == 'U') vowels++;
            } else if (c >= '0' && c <= '9') {
                digits++;
            }
        }

        if (mixedLetterDigitTokens >= 1) {
            return true;
        }

        if (longAlnumTokens >= 1 && letters >= 8 && digits >= 2 && vowels <= 1) {
            return true;
        }
        if (letters >= 12 && vowels == 0 && longAlnumTokens >= 2) {
            return true;
        }

        return false;
    }

    private static int qualityScore(List<TransactionData> txs) {
        if (txs == null || txs.isEmpty()) return 0;
        int total = 0;
        int garbled = 0;
        int missingDate = 0;
        for (TransactionData tx : txs) {
            if (tx == null) continue;
            total++;
            if (tx.date == null) missingDate++;
            if (isLikelyGarbledMerchant(tx.description)) garbled++;
        }
        if (total == 0) return 0;

        int score = total * 1000;
        score -= garbled * 250;
        score -= missingDate * 250;
        return score;
    }

    private static BigDecimal sumExpenseAmounts(List<TransactionData> transactions) {
        if (transactions == null || transactions.isEmpty()) return BigDecimal.ZERO;
        BigDecimal sum = BigDecimal.ZERO;
        for (TransactionData tx : transactions) {
            if (tx == null || tx.amount == null) continue;
            if (tx.type != null && tx.type != com.ella.backend.enums.TransactionType.EXPENSE) continue;
            sum = sum.add(tx.amount.abs());
        }
        return sum;
    }

    private static int countDigits(String value) {
        if (value == null || value.isEmpty()) return 0;
        int count = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= '0' && c <= '9') count++;
        }
        return count;
    }
}
