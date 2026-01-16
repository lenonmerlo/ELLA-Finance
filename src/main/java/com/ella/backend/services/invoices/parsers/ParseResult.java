package com.ella.backend.services.invoices.parsers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParseResult {
    private List<TransactionData> transactions;
    private LocalDate dueDate;
    private BigDecimal totalAmount;
    private String cardLastDigits;
    private String bankName;

    // Quality score
    private int qualityScore;
    private String source;
    private String rawText;

    /**
     * Verifica se o resultado é válido para aceitar.
     *
     * Nota: Este método agora é DEPRECATED.
     * Use ParseQualityValidator.isValid(parseResult, config) em vez disso.
     *
     * @deprecated Use {@link com.ella.backend.services.invoices.quality.ParseQualityValidator#isValid(
     *     com.ella.backend.services.invoices.parsers.ParseResult,
     *     com.ella.backend.config.QualityScoreConfig)}
     */
    @Deprecated(since = "1.1.0", forRemoval = true)
    public boolean isValid() {
        return qualityScore >= 50
                && transactions != null
                && transactions.size() >= 3
                && dueDate != null
                && totalAmount != null
                && totalAmount.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Verifica se é alta qualidade.
     *
     * Nota: Este método agora é DEPRECATED.
     * Use ParseQualityValidator.isHighQuality(parseResult, config) em vez disso.
     *
     * @deprecated Use {@link com.ella.backend.services.invoices.quality.ParseQualityValidator#isHighQuality(
     *     com.ella.backend.services.invoices.parsers.ParseResult,
     *     com.ella.backend.config.QualityScoreConfig)}
     */
    @Deprecated(since = "1.1.0", forRemoval = true)
    public boolean isHighQuality() {
        return qualityScore >= 75;
    }

    public String getDescription() {
        return String.format(
                "ParseResult{source=%s, score=%d, transactions=%d, dueDate=%s, total=%s}",
                source,
                qualityScore,
                transactions != null ? transactions.size() : 0,
                dueDate,
                totalAmount);
    }
}
