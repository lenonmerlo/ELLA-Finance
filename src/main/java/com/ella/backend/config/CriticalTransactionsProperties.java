package com.ella.backend.config;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ella.critical")
public record CriticalTransactionsProperties(
        boolean enabled,
        BigDecimal amountThreshold,
        List<String> riskyCategories,
        List<String> suspiciousDescriptionKeywords
) {
    public CriticalTransactionsProperties {
        if (amountThreshold == null) {
            amountThreshold = new BigDecimal("5000");
        }
        if (riskyCategories == null) {
            riskyCategories = List.of();
        }
        if (suspiciousDescriptionKeywords == null) {
            suspiciousDescriptionKeywords = List.of();
        }
    }
}
