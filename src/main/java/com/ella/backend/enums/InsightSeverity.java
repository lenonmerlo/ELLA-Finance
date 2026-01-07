package com.ella.backend.enums;

public enum InsightSeverity {
    LOW("Baixa"),
    MEDIUM("Média"),
    HIGH("Alta");

    private final String displayName;

    InsightSeverity(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
