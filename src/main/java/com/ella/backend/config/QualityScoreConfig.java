package com.ella.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * Configurações de Quality Score.
 * Carrega de application.properties com prefixo "extraction.quality".
 *
 * Exemplo:
 * extraction.quality.min-score-for-acceptance=50
 * extraction.quality.min-score-for-high-quality=75
 * extraction.quality.min-transactions=3
 * extraction.quality.min-text-length=800
 * extraction.quality.max-garbled-percent=5
 */
@Data
@Component
@ConfigurationProperties(prefix = "extraction.quality")
public class QualityScoreConfig {

    /**
     * Score mínimo para aceitar uma extração.
     */
    private int minScoreForAcceptance = 50;

    /**
     * Score mínimo para considerar "alta qualidade".
     */
    private int minScoreForHighQuality = 75;

    /**
     * Número mínimo de transações para aceitar.
     */
    private int minTransactions = 3;

    /**
     * Comprimento mínimo do texto extraído (em caracteres).
     */
    private int minTextLength = 800;

    /**
     * Percentual máximo de caracteres "quebrados" permitido.
     */
    private double maxGarbledPercent = 5.0;

    public String getDescription() {
        return String.format(
                "QualityScoreConfig{acceptance=%d, highQuality=%d, minTx=%d, minText=%d, maxGarbled=%.1f%%}",
                minScoreForAcceptance,
                minScoreForHighQuality,
                minTransactions,
                minTextLength,
                maxGarbledPercent);
    }
}
