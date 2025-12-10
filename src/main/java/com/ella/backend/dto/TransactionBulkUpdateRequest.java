package com.ella.backend.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

public record TransactionBulkUpdateRequest(
        @NotBlank(message = "personId é obrigatório")
        String personId,
        @NotEmpty(message = "updates não pode ser vazio")
        List<TransactionBulkUpdateItem> updates
) {}
