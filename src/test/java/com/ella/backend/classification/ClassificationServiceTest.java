package com.ella.backend.classification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.ella.backend.classification.dto.ClassificationSuggestResponseDTO;
import com.ella.backend.classification.entity.CategoryFeedback;
import com.ella.backend.classification.entity.CategoryRule;
import com.ella.backend.classification.repository.CategoryFeedbackRepository;
import com.ella.backend.classification.repository.CategoryRuleRepository;
import com.ella.backend.enums.TransactionType;

class ClassificationServiceTest {

    @Mock
    private CategoryRuleRepository ruleRepository;

    @Mock
    private CategoryFeedbackRepository feedbackRepository;

    private ClassificationService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new ClassificationService(ruleRepository, feedbackRepository);
    }

    @Test
    void explicitRule_hasHighestPriority() {
        UUID userId = UUID.randomUUID();

        CategoryRule rule = CategoryRule.builder()
                .userId(userId)
                .pattern("UBER")
                .category("RegraCategoria")
                .priority(10)
                .createdAt(LocalDateTime.now())
                .build();

        when(ruleRepository.findByUserIdOrderByPriorityDescCreatedAtDesc(userId))
                .thenReturn(List.of(rule));

        ClassificationSuggestResponseDTO res = service.suggest(
                userId,
                "uber mercado",
                new BigDecimal("-10.00"),
                null
        );

        assertEquals("RegraCategoria", res.category());
        assertEquals(0.92, res.confidence(), 0.0001);
        assertTrue(res.reason().startsWith("matched rule:"));

        verify(feedbackRepository, never()).findSimilarFeedback(any(), anyString());
    }

    @Test
    void feedbackHistory_isUsedWhenNoRuleMatches() {
        UUID userId = UUID.randomUUID();

        when(ruleRepository.findByUserIdOrderByPriorityDescCreatedAtDesc(userId))
                .thenReturn(List.of());

        CategoryFeedback f1 = CategoryFeedback.builder()
                .userId(userId)
                .transactionId(UUID.randomUUID())
                .chosenCategory("Mercado")
                .createdAt(LocalDateTime.now().minusDays(1))
                .build();

        CategoryFeedback f2 = CategoryFeedback.builder()
                .userId(userId)
                .transactionId(UUID.randomUUID())
                .chosenCategory("Mercado")
                .createdAt(LocalDateTime.now())
                .build();

        when(feedbackRepository.findSimilarFeedback(eq(userId), anyString()))
                .thenReturn(List.of(f2, f1));

        ClassificationSuggestResponseDTO res = service.suggest(
                userId,
                "mercado carrefour",
                new BigDecimal("20.00"),
                TransactionType.EXPENSE
        );

        assertEquals("Mercado", res.category());
        assertEquals(TransactionType.EXPENSE, res.type());
        assertEquals(0.90, res.confidence(), 0.0001);
        assertEquals("feedback history match", res.reason());
    }

    @Test
    void keywordScoring_worksAndReturnsConfidenceFromScore() {
        UUID userId = UUID.randomUUID();

        when(ruleRepository.findByUserIdOrderByPriorityDescCreatedAtDesc(userId))
                .thenReturn(List.of());
        when(feedbackRepository.findSimilarFeedback(eq(userId), anyString()))
                .thenReturn(List.of());

        // "uber mercado" -> Transporte(0.90) vs Mercado(0.70) => Transporte, score=0.90 => confidence=0.85
        ClassificationSuggestResponseDTO res = service.suggest(
                userId,
                "uber mercado",
                new BigDecimal("50.00"),
                null
        );

        assertEquals("Transporte", res.category());
        assertEquals(0.85, res.confidence(), 0.0001);
        assertTrue(res.reason().startsWith("keyword score:"));
    }

    @Test
    void fallbackToOutros_whenNoRuleNoFeedbackNoKeyword() {
        UUID userId = UUID.randomUUID();

        when(ruleRepository.findByUserIdOrderByPriorityDescCreatedAtDesc(userId))
                .thenReturn(List.of());
        when(feedbackRepository.findSimilarFeedback(eq(userId), anyString()))
                .thenReturn(List.of());

        ClassificationSuggestResponseDTO res = service.suggest(
                userId,
                "descricao totalmente desconhecida",
                new BigDecimal("10.00"),
                null
        );

        assertEquals("Outros", res.category());
        assertEquals(0.50, res.confidence(), 0.0001);
        assertEquals("fallback", res.reason());
    }

    @Test
    void confidenceCalculation_isDeterministicForHighScore() {
        UUID userId = UUID.randomUUID();

        when(ruleRepository.findByUserIdOrderByPriorityDescCreatedAtDesc(userId))
                .thenReturn(List.of());
        when(feedbackRepository.findSimilarFeedback(eq(userId), anyString()))
                .thenReturn(List.of());

        // "uber 99" -> Transporte 0.90 + 0.80 = 1.70 => confidence=0.92
        ClassificationSuggestResponseDTO res = service.suggest(
                userId,
                "uber 99",
                new BigDecimal("10.00"),
                null
        );

        assertEquals("Transporte", res.category());
        assertEquals(0.92, res.confidence(), 0.0001);
    }
}
