package com.ella.backend.dto;

import com.ella.backend.enums.TransactionStatus;
import com.ella.backend.enums.TransactionType;
import com.ella.backend.enums.TransactionScope;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record FinancialTransactionResponseDTO(
        String id,
        String personId,
        String personName,
        String description,
        BigDecimal amount,
        TransactionType type,
        TransactionScope scope,
        String category,
        LocalDate transactionDate,
        LocalDate dueDate,
        LocalDate paidDate,
        TransactionStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}