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
}
