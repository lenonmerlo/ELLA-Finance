package com.ella.backend.dto;

import java.util.List;
import java.util.Map;

import lombok.Data;

@Data
public class InvoiceInsightsDTO {

    private Map<String, Double> spendingByCategory;

    /**
     * Ex.: 0.18 para +18% vs mês anterior. Pode ser null se não houver comparativo.
     */
    private Double comparisonWithPreviousMonth;

    private FinancialTransactionResponseDTO highestTransaction;

    private List<FinancialTransactionResponseDTO> recurringSubscriptions;
}
