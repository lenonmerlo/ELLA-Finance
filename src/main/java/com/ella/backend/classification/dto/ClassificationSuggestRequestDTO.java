package com.ella.backend.classification.dto;

import java.math.BigDecimal;

import com.ella.backend.enums.TransactionType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ClassificationSuggestRequestDTO(
        @NotBlank(message = "Descrição é obrigatória")
        String description,

        @NotNull(message = "Valor é obrigatório")
        BigDecimal amount,

        TransactionType type
) {}
