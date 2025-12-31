package com.ella.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.ella.backend.enums.CriticalReason;
import com.ella.backend.enums.TransactionScope;
import com.ella.backend.enums.TransactionStatus;
import com.ella.backend.enums.TransactionType;

public record CriticalTransactionResponseDTO(
        String id,
        String personId,
        String personName,
        String description,
        BigDecimal amount,
        TransactionType type,
        TransactionScope scope,
        String category,
        String tripId,
        String tripSubcategory,
        LocalDate transactionDate,
        LocalDate purchaseDate,
        LocalDate dueDate,
        LocalDate paidDate,
        TransactionStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean critical,
        CriticalReason criticalReason,
        boolean criticalReviewed,
        LocalDateTime criticalReviewedAt
) {}
