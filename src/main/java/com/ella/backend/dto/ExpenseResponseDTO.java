// src/main/java/com/ella/backend/dto/ExpenseResponseDTO.java
package com.ella.backend.dto;

import com.ella.backend.enums.TransactionStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ExpenseResponseDTO {

    private String id;
    private String personId;
    private String personName;

    private String description;
    private BigDecimal amount;
    private String category;

    private LocalDate transactionDate;
    private LocalDate dueDate;
    private LocalDate paidDate;

    private TransactionStatus status;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
