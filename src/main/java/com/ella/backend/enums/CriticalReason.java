package com.ella.backend.enums;

/**
 * Reasons why a transaction was flagged as critical.
 * Keep this additive; values may evolve as detection rules expand.
 */
public enum CriticalReason {
    HIGH_VALUE,
    RISK_CATEGORY,
    SUSPICIOUS_DESCRIPTION,
    DUPLICATE_SAME_DAY,
    OTHER
}
