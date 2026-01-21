package com.ella.backend.enums;

public enum InvestmentType {
    SAVINGS("Poupança"),
    FIXED_INCOME("Renda Fixa"),
    VARIABLE_INCOME("Renda Variável"),
    CRYPTOCURRENCY("Criptomoedas"),
    REAL_ESTATE("Imóvel"),
    OTHER("Outro");

    private final String label;

    InvestmentType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
