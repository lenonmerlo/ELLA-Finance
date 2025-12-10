package com.ella.backend.dto;

import com.ella.backend.enums.TransactionStatus;
import com.ella.backend.enums.TransactionType;
import com.ella.backend.enums.TransactionScope;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FinancialTransactionRequestDTO(

        @NotBlank(message = "personId é obrigatório")
        String personId,

        @NotBlank(message = "Descrição é obrigatória")
        String description,

        @NotNull(message = "Valor é obrigatório")
        @Positive(message = "Valor deve ser maior que zero")
        BigDecimal amount,

        @NotNull(message = "Tipo é obrigatório")
        TransactionType type,

        TransactionScope scope,

        @NotBlank(message = "Categoria é obrigatória")
        String category,

        @NotNull(message = "Data da transação é obrigatória")
        LocalDate transactionDate,

        LocalDate dueDate,

        LocalDate paidDate,

        @NotNull(message = "Status é obrigatório")
        TransactionStatus status
) {}
