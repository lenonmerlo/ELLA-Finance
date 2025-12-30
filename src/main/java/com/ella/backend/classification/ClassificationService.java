package com.ella.backend.classification;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ella.backend.classification.dto.ClassificationSuggestResponseDTO;
import com.ella.backend.classification.entity.CategoryFeedback;
import com.ella.backend.classification.entity.CategoryRule;
import com.ella.backend.classification.repository.CategoryFeedbackRepository;
import com.ella.backend.classification.repository.CategoryRuleRepository;
import com.ella.backend.classification.rules.KeywordHeuristics;
import com.ella.backend.classification.rules.MerchantMappings;
import com.ella.backend.enums.TransactionType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClassificationService {

    private final CategoryRuleRepository categoryRuleRepository;
    private final CategoryFeedbackRepository categoryFeedbackRepository;

    public ClassificationSuggestResponseDTO suggest(UUID userId, String description, BigDecimal amount, TransactionType explicitType) {
        String normalizedDescription = normalize(description);
        String looseNormalized = MerchantMappings.looseNormalize(description);
        TransactionType type = resolveType(amount, explicitType);

        log.debug("Classification suggest userId={} normalized='{}'", userId, normalizedDescription);

        // 1) Regras explícitas do usuário
        for (CategoryRule rule : categoryRuleRepository.findByUserIdOrderByPriorityDescCreatedAtDesc(userId)) {
            String rulePattern = normalize(rule.getPattern());
            if (!rulePattern.isBlank() && normalizedDescription.contains(rulePattern)) {
                log.debug("Matched explicit rule pattern='{}' -> category='{}'", rule.getPattern(), rule.getCategory());
                return new ClassificationSuggestResponseDTO(
                        rule.getCategory(),
                        type,
                        0.92,
                        "matched rule: " + rule.getPattern()
                );
            }
        }

        // 2) Histórico de feedback do usuário
        var feedbackMatch = findFeedbackMatch(userId, normalizedDescription);
        if (feedbackMatch.isPresent()) {
            log.debug("Matched feedback history -> category='{}'", feedbackMatch.get());
            return new ClassificationSuggestResponseDTO(
                    feedbackMatch.get(),
                    type,
                    0.90,
                    "feedback history match"
            );
        }

        // 3) Mapeamentos curados de comerciantes (alta precisão)
        var merchantMatch = MerchantMappings.bestMatch(looseNormalized);
        if (merchantMatch.isPresent()) {
            var m = merchantMatch.get();
            log.debug("Matched merchant mapping pattern='{}' -> category='{}' confidence={}", m.merchantPattern(), m.category(), m.confidence());
            return new ClassificationSuggestResponseDTO(
                m.category(),
                type,
                m.confidence(),
                "merchant mapping: " + m.merchantPattern()
            );
        }

        // 4) Heurística por score (pesos)
        Map<String, Double> scores = calculateCategoryScores(normalizedDescription);
        var best = scores.entrySet().stream()
                .max(Map.Entry.comparingByValue());

        if (best.isPresent() && best.get().getValue() > 0.0) {
            String bestCategory = best.get().getKey();
            double bestScore = best.get().getValue();
            double confidence = confidenceFromScore(bestScore);
            String reason = buildScoreReason(bestCategory, bestScore, normalizedDescription);

            log.debug("Matched keyword scoring -> category='{}' score={} confidence={} reason='{}'", bestCategory, bestScore, confidence, reason);
            return new ClassificationSuggestResponseDTO(
                    bestCategory,
                    type,
                    confidence,
                    reason
            );
        }

        // 5) Fallback
        return new ClassificationSuggestResponseDTO(
                "Outros",
                type,
                0.50,
                "fallback"
        );
    }

    /**
     * Calcula score total por categoria com base nas keywords presentes.
     */
    private Map<String, Double> calculateCategoryScores(String normalizedDescription) {
        Map<String, Double> scores = new HashMap<>();
        for (Map.Entry<String, Map<String, Double>> byCategory : KeywordHeuristics.CATEGORY_KEYWORD_WEIGHTS.entrySet()) {
            String category = byCategory.getKey();
            double total = 0.0;
            for (Map.Entry<String, Double> kw : byCategory.getValue().entrySet()) {
                if (normalizedDescription.contains(kw.getKey())) {
                    total += kw.getValue();
                }
            }
            if (total > 0.0) {
                scores.put(category, total);
            }
        }
        return scores;
    }

    /**
     * Busca feedbacks anteriores (por descrição similar) e retorna a categoria mais frequente.
     *
     * Observação: a tabela category_feedback não armazena a descrição; portanto a busca é feita via join
     * com financial_transactions usando transaction_id.
     */
    private Optional<String> findFeedbackMatch(UUID userId, String normalizedDescription) {
        if (normalizedDescription == null || normalizedDescription.isBlank()) {
            return Optional.empty();
        }

        // limita o tamanho do termo para evitar LIKE gigante e reduzir falsos negativos
        String likeTerm = normalizedDescription.length() > 80 ? normalizedDescription.substring(0, 80) : normalizedDescription;

        List<CategoryFeedback> feedbacks = categoryFeedbackRepository.findSimilarFeedback(userId, likeTerm);
        if (feedbacks == null || feedbacks.isEmpty()) {
            return Optional.empty();
        }

        // contabiliza por chosenCategory (mais frequente ganha). Em empate, usa o mais recente.
        Map<String, Integer> counts = new HashMap<>();
        Map<String, CategoryFeedback> latest = new HashMap<>();

        // evita iterar uma lista enorme
        int limit = Math.min(feedbacks.size(), 50);
        for (int i = 0; i < limit; i++) {
            CategoryFeedback f = feedbacks.get(i);
            if (f.getChosenCategory() == null || f.getChosenCategory().isBlank()) continue;
            counts.merge(f.getChosenCategory(), 1, Integer::sum);

            CategoryFeedback currentLatest = latest.get(f.getChosenCategory());
            if (currentLatest == null) {
                latest.put(f.getChosenCategory(), f);
            } else {
                if (f.getCreatedAt() != null && (currentLatest.getCreatedAt() == null || f.getCreatedAt().isAfter(currentLatest.getCreatedAt()))) {
                    latest.put(f.getChosenCategory(), f);
                }
            }
        }

        return counts.entrySet().stream()
                .max(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue)
                        .thenComparing(e -> {
                            CategoryFeedback l = latest.get(e.getKey());
                            return l != null && l.getCreatedAt() != null ? l.getCreatedAt() : java.time.LocalDateTime.MIN;
                        }))
                .map(Map.Entry::getKey);
    }

    private double confidenceFromScore(double score) {
        if (score >= 1.5) return 0.92;
        if (score >= 1.0) return 0.88;
        if (score >= 0.9) return 0.85;
        if (score >= 0.7) return 0.80;
        if (score >= 0.5) return 0.72;
        return 0.65;
    }

    private String buildScoreReason(String bestCategory, double bestScore, String normalizedDescription) {
        // lista as keywords que contribuíram para o score da categoria escolhida
        List<String> matched = new ArrayList<>();
        Map<String, Double> kws = KeywordHeuristics.CATEGORY_KEYWORD_WEIGHTS.get(bestCategory);
        if (kws != null) {
            for (Map.Entry<String, Double> kw : kws.entrySet()) {
                if (normalizedDescription.contains(kw.getKey())) {
                    matched.add(kw.getKey() + "(" + kw.getValue() + ")");
                }
            }
        }

        matched.sort(String::compareTo);
        if (matched.isEmpty()) {
            return "keyword score: " + String.format(Locale.ROOT, "%.2f", bestScore);
        }
        return "keyword score: " + String.format(Locale.ROOT, "%.2f", bestScore) + " matched=" + String.join(",", matched);
    }

    private TransactionType resolveType(BigDecimal amount, TransactionType explicitType) {
        if (explicitType != null) {
            // Mantém compatibilidade com seu enum rico: se vier algo diferente de INCOME/EXPENSE, respeita.
            return explicitType;
        }
        if (amount == null) return TransactionType.EXPENSE;
        return amount.compareTo(BigDecimal.ZERO) < 0 ? TransactionType.INCOME : TransactionType.EXPENSE;
    }

    public String normalize(String input) {
        if (input == null) return "";
        String s = input.trim().toLowerCase(Locale.ROOT);
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        s = s.replaceAll("\\s+", " ");
        return s;
    }
}
