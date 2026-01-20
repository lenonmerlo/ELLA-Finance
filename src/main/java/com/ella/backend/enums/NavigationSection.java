package com.ella.backend.enums;

/**
 * Enum que centraliza todas as seções de navegação do dashboard.
 * Facilita manutenção e sincronização com o Frontend.
 */
public enum NavigationSection {
    OVERVIEW("overview", "Saúde financeira"),
    INVOICES("invoices", "Faturas de cartão"),
    TRANSACTIONS("transactions", "Lançamentos Cartão"),
    BANK_STATEMENTS("bank-statements", "Movimentação C/C"),
    CHARTS("charts", "Gráficos"),
    GOALS("goals", "Metas"),
    INSIGHTS("insights", "Insights da Ella");

    private final String id;
    private final String label;

    NavigationSection(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    /**
     * Busca uma seção pelo ID.
     * @param id O ID da seção
     * @return A seção correspondente, ou OVERVIEW se não encontrada
     */
    public static NavigationSection fromId(String id) {
        if (id == null) {
            return OVERVIEW;
        }
        for (NavigationSection section : values()) {
            if (section.id.equals(id)) {
                return section;
            }
        }
        return OVERVIEW; // default
    }
}
