package com.ella.backend.classification;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ella.backend.classification.dto.ClassificationSuggestResponseDTO;
import com.ella.backend.classification.entity.CategoryRule;
import com.ella.backend.classification.repository.CategoryRuleRepository;
import com.ella.backend.classification.rules.KeywordHeuristics;
import com.ella.backend.enums.TransactionType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClassificationService {

    private final CategoryRuleRepository categoryRuleRepository;

    public ClassificationSuggestResponseDTO suggest(UUID userId, String description, BigDecimal amount, TransactionType explicitType) {
        String normalizedDescription = normalize(description);

        // 1) Regras explícitas do usuário
        for (CategoryRule rule : categoryRuleRepository.findByUserIdOrderByPriorityDescCreatedAtDesc(userId)) {
            String rulePattern = normalize(rule.getPattern());
            if (!rulePattern.isBlank() && normalizedDescription.contains(rulePattern)) {
                return new ClassificationSuggestResponseDTO(
                        rule.getCategory(),
                        resolveType(amount, explicitType),
                        0.92,
                        "matched rule: " + rule.getPattern()
                );
            }
        }

        // 2) Heurísticas por keyword
        for (Map.Entry<String, String> entry : KeywordHeuristics.KEYWORD_TO_CATEGORY.entrySet()) {
            if (normalizedDescription.contains(entry.getKey())) {
                return new ClassificationSuggestResponseDTO(
                        entry.getValue(),
                        resolveType(amount, explicitType),
                        0.86,
                        "keyword: " + entry.getKey()
                );
            }
        }

        // 3) Fallback
        return new ClassificationSuggestResponseDTO(
                "Outros",
                resolveType(amount, explicitType),
                0.50,
                "fallback"
        );
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
