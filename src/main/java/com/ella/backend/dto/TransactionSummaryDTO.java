package com.ella.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.ella.backend.enums.TransactionType;

import lombok.Data;

@Data
public class TransactionSummaryDTO {
    private String id;
    private String description;
    private BigDecimal amount;
    private TransactionType type;
    private String category;
    private LocalDate transactionDate;
}
