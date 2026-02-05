package com.ella.backend.enums;

/**
 * Status de cobrança calculado internamente (sem depender de gateway).
 */
public enum BillingStatus {
    UP_TO_DATE,
    OVERDUE,
    NO_SUBSCRIPTION,
    CANCELED
}
