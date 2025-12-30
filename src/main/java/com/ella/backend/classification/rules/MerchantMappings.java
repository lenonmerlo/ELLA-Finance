package com.ella.backend.classification.rules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Curated merchant patterns observed in invoice/statement descriptions.
 *
 * Matching is done against a "loose normalized" description (lowercase, no accents,
 * and with non-alphanumeric characters treated as spaces).
 */
public final class MerchantMappings {

    private MerchantMappings() {}

    public record MerchantMapping(String merchantPattern, String category, double confidence, List<String> fragments) {
        public MerchantMapping {
            if (merchantPattern == null || merchantPattern.isBlank()) throw new IllegalArgumentException("merchantPattern is required");
            if (category == null || category.isBlank()) throw new IllegalArgumentException("category is required");
            if (confidence < 0.0 || confidence > 1.0) throw new IllegalArgumentException("confidence must be between 0.0 and 1.0");
            if (fragments == null || fragments.isEmpty()) throw new IllegalArgumentException("fragments is required");
        }

        public boolean matchesLoose(String looseNormalizedDescription) {
            if (looseNormalizedDescription == null || looseNormalizedDescription.isBlank()) return false;
            for (String f : fragments) {
                if (f == null || f.isBlank()) continue;
                if (!looseNormalizedDescription.contains(f)) {
                    return false;
                }
            }
            return true;
        }

        public int specificityScore() {
            // Prefer more specific patterns (more/longer fragments)
            int sum = 0;
            for (String f : fragments) {
                if (f != null) sum += f.length();
            }
            return sum;
        }
    }

    public static final List<MerchantMapping> MAPPINGS;

    static {
        List<MerchantMapping> items = new ArrayList<>();

        // Saúde
        items.add(mapping("EINSTEIN MORUMBI", "Saúde", 0.95));
        items.add(mapping("FRANCISCO FERNANDO MOR", "Saúde", 0.85));

        // Seguros
        items.add(mapping("Prudent*APOL", "Seguros", 0.95));
        items.add(mapping("BRADESCO AUT*", "Seguros", 0.90));

        // Viagem
        items.add(mapping("IBERIA LAE SA EUR", "Viagem", 0.95));
        items.add(mapping("AGENCIA DE VIAGEM", "Viagem", 0.95));
        items.add(mapping("MP*FAPASSAGENS", "Viagem", 0.95));

        // Alimentação
        items.add(mapping("Vindi *Ddottasushi", "Alimentação", 0.90));
        items.add(mapping("COCINA 378", "Alimentação", 0.90));
        items.add(mapping("DULCE SILVEIRA", "Alimentação", 0.80));
        items.add(mapping("PAYGO*NABIRRA ACA", "Alimentação", 0.60));

        // Vestuário
        items.add(mapping("MulherCheirosa", "Vestuário", 0.85));
        items.add(mapping("CORTEZ E BELCHIOR", "Vestuário", 0.50));

        // Educação
        items.add(mapping("SANAR", "Educação", 0.90));

        // Lazer
        items.add(mapping("MP*NOVOTICKET", "Lazer", 0.75));

        // Diversos
        items.add(mapping("SHOPPING LIVELO", "Diversos", 0.70));
        items.add(mapping("DECORART COMERCIO", "Diversos", 0.70));
        items.add(mapping("MP*EXCLUSIVSERVI", "Diversos", 0.60));
        items.add(mapping("ASAAS*RADIOMIND", "Diversos", 0.50));

        MAPPINGS = Collections.unmodifiableList(items);
    }

    public static Optional<MerchantMapping> bestMatch(String looseNormalizedDescription) {
        if (looseNormalizedDescription == null || looseNormalizedDescription.isBlank()) {
            return Optional.empty();
        }

        return MAPPINGS.stream()
                .filter(m -> m.matchesLoose(looseNormalizedDescription))
                .max(Comparator
                        .comparingDouble(MerchantMapping::confidence)
                        .thenComparingInt(MerchantMapping::specificityScore));
    }

    private static MerchantMapping mapping(String pattern, String category, double confidence) {
        String p = Objects.requireNonNullElse(pattern, "").trim();
        String loose = looseNormalize(p);

        List<String> fragments = new ArrayList<>();
        for (String part : loose.split("\\s+")) {
            String f = part.trim();
            if (!f.isBlank()) fragments.add(f);
        }

        // For wildcard-like patterns (e.g. MP*FOO), looseNormalize turns into tokens already.
        // We keep all tokens as required fragments; this makes matching precise.
        return new MerchantMapping(p, category, confidence, Collections.unmodifiableList(fragments));
    }

    /**
     * Lowercase + remove accents + convert non-alphanumeric to spaces.
     * This is intentionally similar to ClassificationService.normalize(), but looser
     * to make statement text matching resilient to punctuation like '*', '-', '/', etc.
     */
    public static String looseNormalize(String input) {
        if (input == null) return "";
        String s = input.trim().toLowerCase(Locale.ROOT);
        s = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        s = s.replaceAll("[^a-z0-9]+", " ");
        s = s.replaceAll("\\s+", " ").trim();
        return s;
    }
}
