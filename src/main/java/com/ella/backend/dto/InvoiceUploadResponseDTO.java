package com.ella.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InvoiceUploadResponseDTO {
    private UUID invoiceId;
    private BigDecimal totalAmount;
    private int totalTransactions;
    private LocalDate startDate;
    private LocalDate endDate;
    private List<FinancialTransactionResponseDTO> transactions;
    private TripSuggestionDTO tripSuggestion;
    private CaptureSummaryDTO captureSummary;
    private List<String> unmatchedTransactions;

    @Data
    @Builder
    public static class CaptureSummaryDTO {
        private BigDecimal invoiceAmount;
        private BigDecimal capturedAmount;
        private BigDecimal differenceAmount;
        private BigDecimal coveragePercent;
        private BigDecimal previousBalance;
        private BigDecimal creditsPayments;
        private BigDecimal purchasesDebits;
        private BigDecimal previousOutstandingAmount;
        private BigDecimal periodDifferenceAmount;
        private BigDecimal periodCoveragePercent;
        private boolean periodMatch;
        private boolean modalRecommended;
    }
}
