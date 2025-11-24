// src/main/java/com/ella/backend/dto/ExpenseRequestDTO.java
package com.ella.backend.dto;

import com.ella.backend.enums.TransactionStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ExpenseRequestDTO {

    @NotBlank(message = "personId é obrigatório")
    private String personId;

    @NotBlank(message = "Descrição é obrigatória")
    private String description;

    @NotNull(message = "Valor é obrigatório")
    @DecimalMin(value = "0.01", message = "Valor deve ser maior que zero")
    private BigDecimal amount;

    @NotNull(message = "Data da transação é obrigatória")
    private LocalDate transactionDate;

    // opcionais
    private String category;
    private LocalDate dueDate;
    private LocalDate paidDate;

    @NotNull(message = "Status é obrigatório")
    private TransactionStatus status;
}
