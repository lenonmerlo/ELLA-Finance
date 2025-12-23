// src/main/java/com/ella/backend/dto/InvoiceResponseDTO.java
package com.ella.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.ella.backend.entities.Person;
import com.ella.backend.enums.InvoiceStatus;

import lombok.Data;

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

    private LocalDate paidDate;

    private InvoiceStatus status;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Resumo não persistente de transações associadas à fatura (sem atributo person)
    private java.util.List<TransactionSummaryDTO> resume;

    // Dados da pessoa (owner) do cartão desta fatura
    private Person person;
}
