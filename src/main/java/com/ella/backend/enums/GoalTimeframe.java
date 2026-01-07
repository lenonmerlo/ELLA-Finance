package com.ella.backend.enums;

public enum GoalTimeframe {
    ONE_WEEK("1 semana"),
    TWO_WEEKS("2 semanas"),
    ONE_MONTH("1 mês"),
    THREE_MONTHS("3 meses");

    private final String displayName;

    GoalTimeframe(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
