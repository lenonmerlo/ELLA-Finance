package com.ella.backend.services;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.ella.backend.config.CriticalTransactionsProperties;
import com.ella.backend.entities.FinancialTransaction;
import com.ella.backend.enums.CriticalReason;

@Service
public class CriticalTransactionDetectionService {

    private final CriticalTransactionsProperties props;

    public CriticalTransactionDetectionService(CriticalTransactionsProperties props) {
        this.props = props;
    }

    /**
     * Computes critical flags from the current transaction state.
     * This is designed to be safe to run on create/update.
     */
    public void evaluateAndApply(FinancialTransaction tx) {
        if (!props.enabled()) {
            clearCritical(tx);
            return;
        }

        CriticalReason reason = detectReason(tx);
        boolean critical = reason != null;

        if (!critical) {
            clearCritical(tx);
            return;
        }

        boolean wasCritical = tx.isCritical();
        tx.setCritical(true);
        tx.setCriticalReason(reason);

        // If it becomes critical again after being non-critical, reset review.
        if (!wasCritical) {
            tx.setCriticalReviewed(false);
            tx.setCriticalReviewedAt(null);
        }
    }

    private void clearCritical(FinancialTransaction tx) {
        tx.setCritical(false);
        tx.setCriticalReason(null);
        tx.setCriticalReviewed(false);
        tx.setCriticalReviewedAt(null);
    }

    private CriticalReason detectReason(FinancialTransaction tx) {
        if (tx.getAmount() != null && isHighValue(tx.getAmount())) {
            return CriticalReason.HIGH_VALUE;
        }

        if (tx.getCategory() != null && isRiskyCategory(tx.getCategory())) {
            return CriticalReason.RISK_CATEGORY;
        }

        if (tx.getDescription() != null && isSuspiciousDescription(tx.getDescription())) {
            return CriticalReason.SUSPICIOUS_DESCRIPTION;
        }

        return null;
    }

    private boolean isHighValue(BigDecimal amount) {
        return amount.compareTo(props.amountThreshold()) >= 0;
    }

    private boolean isRiskyCategory(String category) {
        String normalized = normalize(category);
        return props.riskyCategories().stream()
                .map(this::normalize)
                .anyMatch(r -> !r.isBlank() && normalized.equals(r));
    }

    private boolean isSuspiciousDescription(String description) {
        String normalized = normalize(description);
        return props.suspiciousDescriptionKeywords().stream()
                .map(this::normalize)
                .anyMatch(k -> !k.isBlank() && normalized.contains(k));
    }

    private String normalize(String value) {
        String s = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        s = s.replaceAll("[^a-z0-9]+", " ").trim();
        return s;
    }
}
