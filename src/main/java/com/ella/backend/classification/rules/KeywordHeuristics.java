package com.ella.backend.classification.rules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class KeywordHeuristics {

    private KeywordHeuristics() {}

    /**
     * Associação de keywords por categoria, com peso (0.0 a 1.0).
     * As keywords devem estar normalizadas (lowercase e sem acentos).
     */

    public record CategoryKeywordWeight(String category, String keyword, double weight) {
        public CategoryKeywordWeight {
            if (category == null || category.isBlank()) throw new IllegalArgumentException("category is required");
            if (keyword == null || keyword.isBlank()) throw new IllegalArgumentException("keyword is required");
            if (weight < 0.0 || weight > 1.0) throw new IllegalArgumentException("weight must be between 0.0 and 1.0");
        }
    }

    /**
     * Estrutura principal: categoria -> (keyword -> peso)
     * LinkedHashMap para manter ordem determinística nas iterações.
     */
    public static final Map<String, Map<String, Double>> CATEGORY_KEYWORD_WEIGHTS;

    /**
     * Lista flat para iterações simples.
     */
    public static final List<CategoryKeywordWeight> KEYWORDS;

    static {
        List<CategoryKeywordWeight> items = new ArrayList<>();

        // Alimentação
        items.add(new CategoryKeywordWeight("Alimentação", "ifood", 0.95));
        items.add(new CategoryKeywordWeight("Alimentação", "ubereats", 0.90));
        items.add(new CategoryKeywordWeight("Alimentação", "restaurante", 0.60));
        items.add(new CategoryKeywordWeight("Alimentação", "padaria", 0.60));
        items.add(new CategoryKeywordWeight("Alimentação", "lanchonete", 0.60));

        // Transporte
        items.add(new CategoryKeywordWeight("Transporte", "uber", 0.90));
        items.add(new CategoryKeywordWeight("Transporte", "99", 0.80));
        items.add(new CategoryKeywordWeight("Transporte", "posto", 0.60));
        items.add(new CategoryKeywordWeight("Transporte", "ipiranga", 0.80));
        items.add(new CategoryKeywordWeight("Transporte", "shell", 0.80));
        items.add(new CategoryKeywordWeight("Transporte", "combust", 0.50));

        // Mercado
        items.add(new CategoryKeywordWeight("Mercado", "mercado", 0.70));
        items.add(new CategoryKeywordWeight("Mercado", "supermerc", 0.60));
        items.add(new CategoryKeywordWeight("Mercado", "carrefour", 0.90));
        items.add(new CategoryKeywordWeight("Mercado", "assai", 0.90));
        items.add(new CategoryKeywordWeight("Mercado", "atacado", 0.60));

        // Farmácia
        items.add(new CategoryKeywordWeight("Farmácia", "farmacia", 0.70));
        items.add(new CategoryKeywordWeight("Farmácia", "drogaria", 0.70));
        items.add(new CategoryKeywordWeight("Farmácia", "drogasil", 0.90));
        items.add(new CategoryKeywordWeight("Farmácia", "droga", 0.50));

        // Streaming
        items.add(new CategoryKeywordWeight("Streaming", "netflix", 0.95));
        items.add(new CategoryKeywordWeight("Streaming", "spotify", 0.95));
        items.add(new CategoryKeywordWeight("Streaming", "disney", 0.90));
        items.add(new CategoryKeywordWeight("Streaming", "prime", 0.70));
        items.add(new CategoryKeywordWeight("Streaming", "hbo", 0.90));

        // Serviços
        items.add(new CategoryKeywordWeight("Serviços", "amazon", 0.60));
        items.add(new CategoryKeywordWeight("Serviços", "mercado livre", 0.90));
        items.add(new CategoryKeywordWeight("Serviços", "shopee", 0.90));

        // Contas / Fixos
        items.add(new CategoryKeywordWeight("Aluguel", "aluguel", 0.90));
        items.add(new CategoryKeywordWeight("Luz", "energia", 0.60));
        items.add(new CategoryKeywordWeight("Luz", "luz", 0.60));
        items.add(new CategoryKeywordWeight("Água", "agua", 0.60));
        items.add(new CategoryKeywordWeight("Internet", "internet", 0.70));
        items.add(new CategoryKeywordWeight("Celular", "telefone", 0.60));
        items.add(new CategoryKeywordWeight("Celular", "celular", 0.60));

        // Saúde
        items.add(new CategoryKeywordWeight("Saúde", "hospital", 0.80));
        items.add(new CategoryKeywordWeight("Saúde", "clinica", 0.70));
        items.add(new CategoryKeywordWeight("Saúde", "otica", 0.60));
        items.add(new CategoryKeywordWeight("Médico", "consulta", 0.60));

        // Lazer
        items.add(new CategoryKeywordWeight("Lazer", "academia", 0.60));
        items.add(new CategoryKeywordWeight("Lazer", "smartfit", 0.90));
        items.add(new CategoryKeywordWeight("Lazer", "gym", 0.50));

        KEYWORDS = Collections.unmodifiableList(items);
        CATEGORY_KEYWORD_WEIGHTS = Collections.unmodifiableMap(buildCategoryMap(items));
    }

    private static Map<String, Map<String, Double>> buildCategoryMap(List<CategoryKeywordWeight> items) {
        Map<String, Map<String, Double>> byCategory = new LinkedHashMap<>();
        for (CategoryKeywordWeight item : items) {
            byCategory.computeIfAbsent(item.category(), k -> new LinkedHashMap<>()).put(item.keyword(), item.weight());
        }
        // torna todas as sub-maps imutáveis
        Map<String, Map<String, Double>> immutable = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Double>> e : byCategory.entrySet()) {
            immutable.put(e.getKey(), Collections.unmodifiableMap(new LinkedHashMap<>(e.getValue())));
        }
        return immutable;
    }
}
