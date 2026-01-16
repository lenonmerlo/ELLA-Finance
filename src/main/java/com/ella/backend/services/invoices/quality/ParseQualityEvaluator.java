package com.ella.backend.services.invoices.quality;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import com.ella.backend.config.QualityScoreConfig;
import com.ella.backend.services.invoices.parsers.ParseResult;

import lombok.extern.slf4j.Slf4j;

/**
 * Avalia a qualidade de uma extração de fatura.
 * Retorna um score de 0-100 baseado em sinais positivos e negativos.
 */
@Slf4j
@Component
public class ParseQualityEvaluator {

    private final QualityScoreConfig qualityScoreConfig;

    public ParseQualityEvaluator(QualityScoreConfig qualityScoreConfig) {
        this.qualityScoreConfig = qualityScoreConfig;
    }

    /**
     * Avalia a qualidade de um ParseResult.
     *
     * @param result Resultado do parsing
     * @param rawText Texto bruto do PDF
     * @return Score de 0-100
     */
    public int evaluate(ParseResult result, String rawText) {
        if (result == null) {
            log.warn("[ParseQualityEvaluator] ParseResult is null");
            return 0;
        }

        if (qualityScoreConfig == null) {
            log.warn("[ParseQualityEvaluator] QualityScoreConfig is null (defaulting to safe score=0)");
            return 0;
        }

        int score = 0;

        log.debug("[ParseQualityEvaluator] Starting evaluation");

        // ============================================
        // SINAIS POSITIVOS (até 100 pontos)
        // ============================================

        // 1. Data de vencimento encontrada (+20)
        if (result.getDueDate() != null) {
            score += 20;
            log.debug("[ParseQualityEvaluator] ✓ Due date found: +20 (total: {})", score);
        } else {
            log.debug("[ParseQualityEvaluator] ✗ Due date NOT found");
        }

        // 2. Total da fatura encontrado (+20)
        if (result.getTotalAmount() != null && result.getTotalAmount().compareTo(BigDecimal.ZERO) > 0) {
            score += 20;
            log.debug("[ParseQualityEvaluator] ✓ Total amount found: {} +20 (total: {})", result.getTotalAmount(), score);
        } else {
            log.debug("[ParseQualityEvaluator] ✗ Total amount NOT found or is zero");
        }

        // 3. Últimos 4 dígitos do cartão (+10)
        if (hasValidCardDigits(result)) {
            score += 10;
            log.debug("[ParseQualityEvaluator] ✓ Card last digits found: +10 (total: {})", score);
        } else {
            log.debug("[ParseQualityEvaluator] ✗ Card last digits NOT found");
        }

        // 4. Número de transações >= 5 (+20)
        int txCount = result.getTransactions() != null ? result.getTransactions().size() : 0;
        if (txCount >= 5) {
            score += 20;
            log.debug("[ParseQualityEvaluator] ✓ Transaction count >= 5: +20 (total: {})", score);
        } else {
            log.debug("[ParseQualityEvaluator] ✗ Transaction count is only {}", txCount);
        }

        // 5. >= 80% das transações com data + valor válido (+30)
        double validTxPercent = getValidTransactionPercentage(result);
        if (validTxPercent >= 0.80) {
            score += 30;
            log.debug("[ParseQualityEvaluator] ✓ Valid transactions >= 80%: {}% +30 (total: {})",
                    Math.round(validTxPercent * 100), score);
        } else {
            log.debug("[ParseQualityEvaluator] ✗ Valid transactions only {}%", Math.round(validTxPercent * 100));
        }

        // ============================================
        // SINAIS NEGATIVOS (reduz score)
        // ============================================

        // 1. Texto muito curto (< 800 chars) (-30)
        if (rawText != null && rawText.length() < qualityScoreConfig.getMinTextLength()) {
            score -= 30;
            log.debug("[ParseQualityEvaluator] ✗ Text too short: {} chars -30 (total: {})", rawText.length(), score);
        }

        // 2. Muitos caracteres quebrados (> 5%) (-20)
        if (rawText != null && hasGarbledCharacters(rawText)) {
            score -= 20;
            log.debug("[ParseQualityEvaluator] ✗ Too many garbled characters -20 (total: {})", score);
        }

        // 3. Poucas transações (< 3) (-25)
        if (txCount < qualityScoreConfig.getMinTransactions()) {
            score -= 25;
            log.debug("[ParseQualityEvaluator] ✗ Too few transactions: {} -25 (total: {})", txCount, score);
        }

        // 4. Transações sem valor/descrição (-15)
        if (hasInvalidTransactions(result)) {
            score -= 15;
            log.debug("[ParseQualityEvaluator] ✗ Has invalid transactions -15 (total: {})", score);
        }

        // ============================================
        // NORMALIZAR SCORE (0-100)
        // ============================================
        score = Math.max(0, Math.min(100, score));

        log.info("[ParseQualityEvaluator] Final score: {} (transactions: {}, valid: {}%, text: {} chars)",
                score,
                txCount,
                Math.round(validTxPercent * 100),
                rawText != null ? rawText.length() : 0);

        return score;
    }

    private boolean hasValidCardDigits(ParseResult result) {
        return result.getCardLastDigits() != null && result.getCardLastDigits().matches("\\d{4}");
    }

    private double getValidTransactionPercentage(ParseResult result) {
        if (result.getTransactions() == null || result.getTransactions().isEmpty()) {
            return 0;
        }

        long validCount = result.getTransactions().stream()
                .filter(tx -> tx != null
                        && tx.getDate() != null
                        && tx.getAmount() != null
                        && tx.getAmount().compareTo(BigDecimal.ZERO) != 0)
                .count();

        return (double) validCount / result.getTransactions().size();
    }

    private boolean hasGarbledCharacters(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        long garbledCount = text.chars()
                .filter(c -> c == '\ufffd'
                        || (c < 32 && c != '\n' && c != '\r' && c != '\t'))
                .count();

        double garbledPercent = (double) garbledCount / text.length() * 100;
        boolean hasGarbled = garbledPercent > qualityScoreConfig.getMaxGarbledPercent();

        if (hasGarbled) {
            log.debug("[ParseQualityEvaluator] Garbled characters: {}%", Math.round(garbledPercent * 10) / 10.0);
        }

        return hasGarbled;
    }

    private boolean hasInvalidTransactions(ParseResult result) {
        if (result.getTransactions() == null || result.getTransactions().isEmpty()) {
            return false;
        }

        return result.getTransactions().stream()
                .anyMatch(tx -> tx == null
                        || tx.getAmount() == null
                        || tx.getDescription() == null
                        || tx.getDescription().trim().isEmpty());
    }
}
