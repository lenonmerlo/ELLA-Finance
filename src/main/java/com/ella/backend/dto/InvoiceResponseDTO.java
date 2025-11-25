// src/main/java/com/ella/backend/dto/InvoiceResponseDTO.java
package com.ella.backend.dto;

import com.ella.backend.enums.InvoiceStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class InvoiceResponseDTO {

    private String id;

    private String cardId;
    private String cardName;

    private Integer month;
    private Integer year;
    private LocalDate dueDate;

    private BigDecimal totalAmount;
    private BigDecimal paidAmount;

    private InvoiceStatus status;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
