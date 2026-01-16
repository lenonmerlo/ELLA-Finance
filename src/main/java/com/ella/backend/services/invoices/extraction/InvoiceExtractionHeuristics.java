package com.ella.backend.services.invoices.extraction;

import java.util.Locale;

/**
 * Shared heuristics for invoice extraction/parsing.
 *
 * Kept in a dedicated class so {@link ExtractionPipeline} is the single source of truth,
 * while other layers (e.g. upload service/tests) can reference the same logic.
 */
public final class InvoiceExtractionHeuristics {

    private InvoiceExtractionHeuristics() {}

    public static boolean isLikelyGarbledMerchant(String description) {
        if (description == null) return false;
        String d = description.trim();
        if (d.isEmpty()) return false;

        // Ignore common short/expected tokens.
        String upper = d.toUpperCase(Locale.ROOT);
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

        // Common Mercado Pago garbling: tokens like "bPU3OTOSY6GLEy" (long, mixed letters+digits).
        if (mixedLetterDigitTokens >= 1) {
            return true;
        }

        // Fallback: lots of alnum, long tokens, very few vowels.
        if (longAlnumTokens >= 1 && letters >= 8 && digits >= 2 && vowels <= 1) {
            return true;
        }
        if (letters >= 12 && vowels == 0 && longAlnumTokens >= 2) {
            return true;
        }

        return false;
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
