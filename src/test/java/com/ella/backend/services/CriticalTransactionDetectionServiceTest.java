package com.ella.backend.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.ella.backend.config.CriticalTransactionsProperties;
import com.ella.backend.entities.FinancialTransaction;
import com.ella.backend.enums.CriticalReason;
import com.ella.backend.enums.TransactionScope;
import com.ella.backend.enums.TransactionStatus;
import com.ella.backend.enums.TransactionType;

class CriticalTransactionDetectionServiceTest {

    @Test
    void flagsHighValueTransaction() {
        var props = new CriticalTransactionsProperties(true, new BigDecimal("5000"), null, null);
        var service = new CriticalTransactionDetectionService(props);

        FinancialTransaction tx = FinancialTransaction.builder()
                .description("Teste")
            .amount(new BigDecimal("5000"))
                .type(TransactionType.EXPENSE)
                .scope(TransactionScope.PERSONAL)
                .category("Outros")
                .transactionDate(java.time.LocalDate.now())
                .status(TransactionStatus.PENDING)
                .build();

        service.evaluateAndApply(tx);

        assertTrue(tx.isCritical());
        assertEquals(CriticalReason.HIGH_VALUE, tx.getCriticalReason());
        assertFalse(tx.isCriticalReviewed());
        assertNull(tx.getCriticalReviewedAt());
    }

    @Test
    void clearsCriticalWhenDisabled() {
        var props = new CriticalTransactionsProperties(false, new BigDecimal("5000"), null, null);
        var service = new CriticalTransactionDetectionService(props);

        FinancialTransaction tx = FinancialTransaction.builder()
                .description("Teste")
                .amount(new BigDecimal("5000"))
                .type(TransactionType.EXPENSE)
                .scope(TransactionScope.PERSONAL)
                .category("Outros")
                .transactionDate(java.time.LocalDate.now())
                .status(TransactionStatus.PENDING)
                .critical(true)
                .criticalReason(CriticalReason.HIGH_VALUE)
                .criticalReviewed(true)
                .criticalReviewedAt(java.time.LocalDateTime.now())
                .build();

        service.evaluateAndApply(tx);

        assertFalse(tx.isCritical());
        assertNull(tx.getCriticalReason());
        assertFalse(tx.isCriticalReviewed());
        assertNull(tx.getCriticalReviewedAt());
    }
}
