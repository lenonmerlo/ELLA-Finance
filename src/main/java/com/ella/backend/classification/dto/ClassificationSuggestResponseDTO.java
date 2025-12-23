package com.ella.backend.classification.dto;

import com.ella.backend.enums.TransactionType;

public record ClassificationSuggestResponseDTO(
        String category,
        TransactionType type,
        double confidence,
        String reason
) {}
