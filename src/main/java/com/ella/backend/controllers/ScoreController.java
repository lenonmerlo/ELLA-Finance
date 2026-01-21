package com.ella.backend.controllers;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ella.backend.dto.score.ScoreResponse;
import com.ella.backend.entities.Score;
import com.ella.backend.services.ScoreService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/scores")
@RequiredArgsConstructor
public class ScoreController {

    private final ScoreService scoreService;

    @GetMapping("/{personId}")
    @PreAuthorize("hasRole('ADMIN') or @securityService.canAccessPerson(#personId)")
    public ResponseEntity<ScoreResponse> getScore(@PathVariable String personId) {
        Score score = scoreService.calculateScore(UUID.fromString(personId));
        return ResponseEntity.ok(toResponse(score));
    }

    private static ScoreResponse toResponse(Score score) {
        ScoreResponse dto = new ScoreResponse();
        dto.setId(score.getId());
        dto.setPersonId(score.getPerson() != null ? score.getPerson().getId() : null);
        dto.setScoreValue(score.getScoreValue());
        dto.setCalculationDate(score.getCalculationDate());
        dto.setCreditUtilizationScore(score.getCreditUtilizationScore());
        dto.setOnTimePaymentScore(score.getOnTimePaymentScore());
        dto.setSpendingDiversityScore(score.getSpendingDiversityScore());
        dto.setSpendingConsistencyScore(score.getSpendingConsistencyScore());
        dto.setCreditHistoryScore(score.getCreditHistoryScore());
        return dto;
    }
}
