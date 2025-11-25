// src/main/java/com/ella/backend/dto/InstallmentResponseDTO.java
package com.ella.backend.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class InstallmentResponseDTO {

    private String id;

    private String invoiceId;
    private String transactionId;

    private Integer number;
    private Integer total;
    private BigDecimal amount;
    private LocalDate dueDate;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
