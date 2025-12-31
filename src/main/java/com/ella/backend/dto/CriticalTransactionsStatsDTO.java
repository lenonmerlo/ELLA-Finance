package com.ella.backend.dto;

import java.util.Map;

public record CriticalTransactionsStatsDTO(
        long totalCritical,
        long totalUnreviewed,
        Map<String, Long> byReason
) {}
