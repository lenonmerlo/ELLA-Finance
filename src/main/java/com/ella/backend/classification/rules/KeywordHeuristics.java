package com.ella.backend.classification.rules;

import java.util.LinkedHashMap;
import java.util.Map;

public final class KeywordHeuristics {

    private KeywordHeuristics() {}

    /**
     * Ordem importa: o primeiro match vence.
     * As chaves devem estar normalizadas (lowercase e sem acentos).
     */
    public static final Map<String, String> KEYWORD_TO_CATEGORY = new LinkedHashMap<>(Map.ofEntries(
            Map.entry("ifood", "Alimentação"),
            Map.entry("ubereats", "Alimentação"),
            Map.entry("restaurante", "Alimentação"),
            Map.entry("padaria", "Alimentação"),
            Map.entry("lanchonete", "Alimentação"),
            Map.entry("uber", "Transporte"),
            Map.entry("99", "Transporte"),
            Map.entry("posto", "Transporte"),
            Map.entry("ipiranga", "Transporte"),
            Map.entry("shell", "Transporte"),
            Map.entry("combust", "Transporte"),
            Map.entry("mercado", "Mercado"),
            Map.entry("supermerc", "Mercado"),
            Map.entry("carrefour", "Mercado"),
            Map.entry("assai", "Mercado"),
            Map.entry("atacado", "Mercado"),
            Map.entry("farmacia", "Farmácia"),
            Map.entry("drogaria", "Farmácia"),
            Map.entry("drogasil", "Farmácia"),
            Map.entry("droga", "Farmácia"),
            Map.entry("netflix", "Streaming"),
            Map.entry("spotify", "Streaming"),
            Map.entry("disney", "Streaming"),
            Map.entry("prime", "Streaming"),
            Map.entry("hbo", "Streaming"),
            Map.entry("amazon", "Serviços"),
            Map.entry("mercado livre", "Serviços"),
            Map.entry("shopee", "Serviços"),
            Map.entry("aluguel", "Aluguel"),
            Map.entry("energia", "Luz"),
            Map.entry("luz", "Luz"),
            Map.entry("agua", "Água"),
            Map.entry("internet", "Internet"),
            Map.entry("telefone", "Celular"),
            Map.entry("celular", "Celular"),
            Map.entry("hospital", "Saúde"),
            Map.entry("clinica", "Saúde"),
            Map.entry("otica", "Saúde"),
            Map.entry("consulta", "Médico"),
            Map.entry("academia", "Lazer"),
            Map.entry("smartfit", "Lazer"),
            Map.entry("gym", "Lazer")
    ));
}
