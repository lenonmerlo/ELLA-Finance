package com.ella.backend.dto.budget;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import lombok.Data;

@Data
public class BudgetResponse {
    private UUID id;

    // Entrada
    private BigDecimal income;
    private BigDecimal essentialFixedCost;
    private BigDecimal necessaryFixedCost;
    private BigDecimal variableFixedCost;
    private BigDecimal investment;
    private BigDecimal plannedPurchase;
    private BigDecimal protection;

    // Calculados
    private BigDecimal total;
    private BigDecimal balance;

    // Regra 50/30/20
    private BigDecimal necessitiesPercentage;
    private BigDecimal desiresPercentage;
    private BigDecimal investmentsPercentage;

    // Recomendações
    private String recommendation;
    private boolean healthy;

    // Auditoria
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
