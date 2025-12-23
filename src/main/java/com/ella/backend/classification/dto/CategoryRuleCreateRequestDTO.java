package com.ella.backend.classification.dto;

import jakarta.validation.constraints.NotBlank;

public record CategoryRuleCreateRequestDTO(
        @NotBlank(message = "pattern é obrigatório")
        String pattern,

        @NotBlank(message = "category é obrigatória")
        String category,

        Integer priority
) {}
