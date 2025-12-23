package com.ella.backend.classification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ClassificationFeedbackRequestDTO(
        @NotBlank(message = "transactionId é obrigatório")
        String transactionId,

        String suggestedCategory,

        @NotBlank(message = "chosenCategory é obrigatório")
        String chosenCategory,

        @NotNull(message = "confidence é obrigatório")
        Double confidence
) {}
