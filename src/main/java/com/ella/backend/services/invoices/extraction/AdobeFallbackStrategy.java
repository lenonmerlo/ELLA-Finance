package com.ella.backend.services.invoices.extraction;

import org.springframework.stereotype.Component;

import com.ella.backend.config.QualityScoreConfig;
import com.ella.backend.services.invoices.parsers.ParseResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Estratégia de decisão para fallback Adobe.
 *
 * Decide:
 * 1) Quando chamar Adobe (se score do resultado atual está baixo)
 * 2) Qual resultado usar (PDFBox/OCR/etc vs Adobe)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdobeFallbackStrategy {

    public static final String DECISION_PDFBOX = "PDFBOX";
    public static final String DECISION_ADOBE = "ADOBE";
    public static final String DECISION_PDFBOX_FALLBACK = "PDFBOX_FALLBACK";

    private final QualityScoreConfig qualityScoreConfig;

    /**
     * Decide se deve tentar Adobe como fallback.
     */
    public boolean shouldTryAdobeFallback(int currentScore) {
        int threshold = qualityScoreConfig != null ? qualityScoreConfig.getMinScoreForHighQuality() : 75;

        boolean shouldTry = currentScore < threshold;

        if (shouldTry) {
            log.info("[AdobeFallback] Score baixo ({} < {}), tentando fallback Adobe", currentScore, threshold);
        } else {
            log.debug("[AdobeFallback] Score ok ({} >= {}), sem fallback Adobe", currentScore, threshold);
        }

        return shouldTry;
    }

    /**
     * Decide qual resultado usar (PDFBox/OCR/etc ou Adobe).
     */
    public String decideBestResult(ParseResult currentResult, ParseResult adobeResult) {
        if (adobeResult == null) {
            log.warn("[AdobeFallback] Adobe falhou, usando resultado atual como fallback");
            return DECISION_PDFBOX_FALLBACK;
        }

        int currentScore = currentResult != null ? currentResult.getQualityScore() : 0;
        int adobeScore = adobeResult.getQualityScore();

        log.info("[AdobeFallback] Comparando scores: Atual={}, Adobe={}", currentScore, adobeScore);

        if (adobeScore >= currentScore + 20) {
            log.info("[AdobeFallback] Adobe significativamente melhor, usando Adobe");
            return DECISION_ADOBE;
        }

        if (adobeScore >= currentScore - 5) {
            log.info("[AdobeFallback] Scores próximos, preferindo resultado atual");
            return DECISION_PDFBOX;
        }

        if (adobeScore > currentScore) {
            log.info("[AdobeFallback] Adobe melhor, usando Adobe");
            return DECISION_ADOBE;
        }

        log.info("[AdobeFallback] Resultado atual melhor/igual, usando resultado atual");
        return DECISION_PDFBOX;
    }

    public String getDecisionDescription(String decision, int currentScore, int adobeScore) {
        return switch (decision) {
            case DECISION_PDFBOX -> String.format("Usando resultado atual (score: %d)", currentScore);
            case DECISION_ADOBE -> String.format("Usando Adobe (score: %d)", adobeScore);
            case DECISION_PDFBOX_FALLBACK -> String.format("Usando resultado atual como fallback (Adobe falhou, score: %d)", currentScore);
            default -> "Decisão desconhecida";
        };
    }
}
