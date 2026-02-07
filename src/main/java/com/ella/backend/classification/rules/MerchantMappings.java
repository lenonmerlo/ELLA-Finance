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
            // Avoid false positives for very short fragments (e.g. "cea" matching inside "nutricea...").
            // For short fragments we require token-boundary match; for longer fragments we keep substring match
            // to support common bank statement suffixes (e.g. "FARFETCH" matching "FARFETCHBR").
            String padded = " " + looseNormalizedDescription + " ";
            for (String f : fragments) {
                if (f == null || f.isBlank()) continue;
                if (f.length() <= 3) {
                    if (!padded.contains(" " + f + " ")) return false;
                } else if (!looseNormalizedDescription.contains(f)) {
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

        // ===== Sicredi: merchants frequentemente caindo em "Outros" =====
        // Observação: preferimos padrões curtos porém específicos, para tolerar sufixos/códigos (ex.: "FARFETCHBR").
        items.add(mapping("LOTERIASONLINE", "Lazer", 0.92));

        // Alimentação
        items.add(mapping("RISTORANTE BENEDETTO", "Alimentação", 0.92));
        items.add(mapping("PAO DA HORA", "Alimentação", 0.90));
        items.add(mapping("CONVE DO MARCAO", "Alimentação", 0.85));
        items.add(mapping("GETULIOS LANCHES", "Alimentação", 0.90));
        items.add(mapping("FS PESCADOS", "Alimentação", 0.90));
        items.add(mapping("CASA FONTANA", "Alimentação", 0.88));
        items.add(mapping("CARNEIRO DO TERCIO", "Alimentação", 0.88));
        items.add(mapping("EVINO", "Alimentação", 0.80));

        // Moda / Acessórios / Casa
        items.add(mapping("FARFETCH", "Vestuário", 0.92));
        items.add(mapping("CORAL CONCEITO", "Vestuário", 0.85));
        items.add(mapping("EC ACESS", "Vestuário", 0.82));
        items.add(mapping("LEROY MERLIN", "Moradia", 0.92));

        // Saúde / Beleza
        items.add(mapping("BELEZA NA WEB", "Saúde", 0.92));

        // Viagens / Turismo
        items.add(mapping("TAP AIR", "Viagem", 0.92));
        items.add(mapping("GRUTA DO MIMOSO", "Viagem", 0.92));
        items.add(mapping("BONITO", "Viagem", 0.70));

        // Serviços / Moradia
        items.add(mapping("SHOPPING LIVELO", "Serviços", 0.70));
        items.add(mapping("DECORART COMERCIO", "Moradia", 0.70));
        items.add(mapping("MP*EXCLUSIVSERVI", "Serviços", 0.60));
        items.add(mapping("ASAAS*RADIOMIND", "Serviços", 0.55));

        // ===== Itaú Personnalité: merchants com OCR colado em datas/sufixos =====
        // Objetivo: reduzir "Outros" quando a descrição vem sem espaços (ex.: LOUNGERIESA, NUTRICEARAPRODNA05/05).
        // Preferimos padrões que ignorem sufixos numéricos variáveis.

        // Vestuário
        items.add(mapping("LOUNGERIE", "Vestuário", 0.92));

        // Saúde
        items.add(mapping("NUTRICEARAPRODNA", "Saúde", 0.85));
        items.add(mapping("JAILTONOCULOS", "Saúde", 0.85));

        // Moradia
        items.add(mapping("CASAFREITAS", "Moradia", 0.85));
        items.add(mapping("SONOESONHOSCOLC", "Moradia", 0.85));
        items.add(mapping("CONSUL", "Moradia", 0.78));

        // Seguros
        items.add(mapping("ALLIANZSEGU", "Seguros", 0.92));

        // Viagem
        items.add(mapping("NANOHOTEIS", "Viagem", 0.92));
        items.add(mapping("SMILESFIDEL", "Viagem", 0.90));
        items.add(mapping("TAMCALLCENTER", "Viagem", 0.90));

        // Transporte
        items.add(mapping("TEMBICI", "Transporte", 0.92));

        // E-commerce
        items.add(mapping("ECOMMERCEEMIASOL", "E-commerce", 0.80));

        // ===== Santander: merchants comuns caindo em "Outros" =====
        // Preferimos marcas/serviços conhecidos; evitamos mapear nomes de pessoas.

        // Viagem
        items.add(mapping("AIRBNB", "Viagem", 0.95));
        items.add(mapping("ORANGE VIAGENS", "Viagem", 0.92));

        // Vestuário
        items.add(mapping("CALVIN KLEIN", "Vestuário", 0.92));
        items.add(mapping("CEA", "Vestuário", 0.90));

        // E-commerce
        items.add(mapping("CASAS BAHIA", "E-commerce", 0.92));

        // Moradia
        items.add(mapping("LECREUSET", "Moradia", 0.90));
        items.add(mapping("LE CREUSET", "Moradia", 0.90));

        // Serviços / Programas
        items.add(mapping("CLUBE ESFERA", "Serviços", 0.90));
        items.add(mapping("ESFERA", "Serviços", 0.75));

        // Taxas
        items.add(mapping("ANUIDADE", "Taxas e Juros", 0.85));

        // Transporte (ex.: estacionamento)
        items.add(mapping("NOVAPARK", "Transporte", 0.85));

        // Alimentação
        items.add(mapping("ANA RISTORANTE ITALIANO", "Alimentação", 0.90));

        // Pets (sem categoria dedicada no frontend -> Serviços)
        items.add(mapping("NUTRIMIXPET", "Serviços", 0.75));

        // ===== Banco do Brasil (BB): taxas e compras internacionais =====
        items.add(mapping("IOF COMPRA NO EXTERIOR", "Taxas e Juros", 0.92));
        items.add(mapping("IOF COMPRA INTERNACIONAL", "Taxas e Juros", 0.92));
        items.add(mapping("IOF", "Taxas e Juros", 0.75));

        // Lazer
        items.add(mapping("911 MUSEUM", "Lazer", 0.88));

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
