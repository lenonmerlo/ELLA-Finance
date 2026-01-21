package com.ella.backend.dto.score;

import java.time.LocalDate;
import java.util.UUID;

import lombok.Data;

@Data
public class ScoreResponse {
    private UUID id;
    private UUID personId;

    private int scoreValue;
    private LocalDate calculationDate;

    private int creditUtilizationScore;
    private int onTimePaymentScore;
    private int spendingDiversityScore;
    private int spendingConsistencyScore;
    private int creditHistoryScore;
}
