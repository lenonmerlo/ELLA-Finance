package com.ella.backend.enums;

public enum AssetType {
    IMOVEL("Imóvel"),
    VEICULO("Veículo"),
    INVESTIMENTO("Investimento"),
    OUTROS("Outros");

    private final String label;

    AssetType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
