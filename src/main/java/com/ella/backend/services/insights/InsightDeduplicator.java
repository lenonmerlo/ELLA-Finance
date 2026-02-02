package com.ella.backend.services.insights;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.ella.backend.dto.dashboard.InsightDTO;

public final class InsightDeduplicator {

    private static final Locale LOCALE_PT_BR = Locale.of("pt", "BR");

    private InsightDeduplicator() {
    }

    /**
     * Remove insights duplicados.
     *
     * Como o {@link InsightDTO} não possui "title" nem período, a deduplicação é feita por:
     * - category
     * - message
     *
     * Mantém a primeira ocorrência (preserva a ordem original).
     */
    public static List<InsightDTO> deduplicate(List<InsightDTO> insights) {
        if (insights == null || insights.isEmpty()) {
            return List.of();
        }

        Map<String, InsightDTO> unique = new LinkedHashMap<>();
        for (InsightDTO insight : insights) {
            if (insight == null) {
                continue;
            }
            String key = normalize(insight.getCategory()) + "|" + normalize(insight.getMessage());
            unique.putIfAbsent(key, insight);
        }

        return new ArrayList<>(unique.values());
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(LOCALE_PT_BR);
    }
}
