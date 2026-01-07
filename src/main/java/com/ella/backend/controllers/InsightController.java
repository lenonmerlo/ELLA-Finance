package com.ella.backend.controllers;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ella.backend.dto.InsightDTO;
import com.ella.backend.entities.Insight;
import com.ella.backend.entities.User;
import com.ella.backend.repositories.InsightRepository;
import com.ella.backend.services.ai.InsightGenerationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/insights")
@RequiredArgsConstructor
@Slf4j
public class InsightController {

    private final InsightGenerationService insightService;
    private final InsightRepository insightRepository;

    /**
     * Retorna insights do usuário em um período específico
     */
    @GetMapping
    public ResponseEntity<List<InsightDTO>> getInsights(
        @RequestParam(required = false) LocalDate startDate,
        @RequestParam(required = false) LocalDate endDate,
        Authentication authentication
    ) {
        User user = getCurrentUser(authentication);

        if (startDate == null) startDate = LocalDate.now().minusMonths(1);
        if (endDate == null) endDate = LocalDate.now();

        List<Insight> insights = insightRepository.findByUserAndGeneratedAtBetween(user, startDate, endDate);
        List<InsightDTO> dtos = insights.stream()
            .map(this::toDTO)
            .collect(Collectors.toList());

        log.info("[InsightController] Retornando {} insights para usuário {}", dtos.size(), user.getId());
        return ResponseEntity.ok(dtos);
    }

    /**
     * Gera novos insights para o usuário
     */
    @PostMapping("/generate")
    public ResponseEntity<List<InsightDTO>> generateInsights(
        @RequestParam(required = false) LocalDate startDate,
        @RequestParam(required = false) LocalDate endDate,
        Authentication authentication
    ) {
        User user = getCurrentUser(authentication);

        if (startDate == null) startDate = LocalDate.now().minusMonths(1);
        if (endDate == null) endDate = LocalDate.now();

        log.info("[InsightController] Gerando insights para usuário {} de {} a {}", 
                 user.getId(), startDate, endDate);

        List<Insight> insights = insightService.generateInsights(user, startDate, endDate);
        List<InsightDTO> dtos = insights.stream()
            .map(this::toDTO)
            .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    private InsightDTO toDTO(Insight insight) {
        return new InsightDTO(
            insight.getId(),
            insight.getTitle(),
            insight.getDescription(),
            insight.getCategory(),
            insight.getSeverity().getDisplayName(),
            insight.isActionable(),
            insight.getGeneratedAt(),
            insight.getStartDate(),
            insight.getEndDate()
        );
    }

    private User getCurrentUser(Authentication authentication) {
        return (User) authentication.getPrincipal();
    }
}
