package com.ella.backend.dto.budget;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BudgetRequest {

    @NotNull(message = "Renda é obrigatória")
    private BigDecimal income;

    @NotNull(message = "Custo Fixo Essencial é obrigatório")
    private BigDecimal essentialFixedCost;

    @NotNull(message = "Custo Fixo Necessário é obrigatório")
    private BigDecimal necessaryFixedCost;

    @NotNull(message = "Custo Fixo Variável é obrigatório")
    private BigDecimal variableFixedCost;

    @NotNull(message = "Investimento é obrigatório")
    private BigDecimal investment;

    @NotNull(message = "Compra Programada é obrigatória")
    private BigDecimal plannedPurchase;

    @NotNull(message = "Proteção é obrigatória")
    private BigDecimal protection;
}
