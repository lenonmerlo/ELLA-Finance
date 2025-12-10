package com.ella.backend.dto;

import com.ella.backend.enums.TransactionScope;

import jakarta.validation.constraints.NotBlank;

public record TransactionBulkUpdateItem(
        @NotBlank(message = "id é obrigatório")
        String id,
        String category,
        TransactionScope scope
) {}
