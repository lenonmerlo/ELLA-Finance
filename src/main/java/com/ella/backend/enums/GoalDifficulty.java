package com.ella.backend.enums;

public enum GoalDifficulty {
    EASY("Fácil"),
    MEDIUM("Médio"),
    HARD("Difícil");

    private final String displayName;

    GoalDifficulty(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
