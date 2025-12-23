package com.ella.backend.classification.dto;

import java.time.LocalDateTime;

public record CategoryRuleResponseDTO(
        String id,
        String pattern,
        String category,
        int priority,
        LocalDateTime createdAt
) {}
