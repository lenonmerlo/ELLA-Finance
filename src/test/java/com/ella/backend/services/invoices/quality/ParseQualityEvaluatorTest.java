package com.ella.backend.services.invoices.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ella.backend.config.QualityScoreConfig;
import com.ella.backend.enums.TransactionScope;
import com.ella.backend.enums.TransactionType;
import com.ella.backend.services.invoices.parsers.ParseResult;
import com.ella.backend.services.invoices.parsers.TransactionData;

class ParseQualityEvaluatorTest {

    @Test
    void evaluate_returnsHighScoreForGoodExtraction() {
        QualityScoreConfig config = new QualityScoreConfig();
        config.setMinTransactions(3);
        config.setMinTextLength(800);
        config.setMaxGarbledPercent(5.0);

        ParseQualityEvaluator evaluator = new ParseQualityEvaluator(config);

        List<TransactionData> txs = List.of(
                new TransactionData("MERCHANT 1", new BigDecimal("10.00"), TransactionType.EXPENSE, "CAT", LocalDate.now(), "CARD", TransactionScope.PERSONAL),
                new TransactionData("MERCHANT 2", new BigDecimal("20.00"), TransactionType.EXPENSE, "CAT", LocalDate.now(), "CARD", TransactionScope.PERSONAL),
                new TransactionData("MERCHANT 3", new BigDecimal("30.00"), TransactionType.EXPENSE, "CAT", LocalDate.now(), "CARD", TransactionScope.PERSONAL),
                new TransactionData("MERCHANT 4", new BigDecimal("40.00"), TransactionType.EXPENSE, "CAT", LocalDate.now(), "CARD", TransactionScope.PERSONAL),
                new TransactionData("MERCHANT 5", new BigDecimal("50.00"), TransactionType.EXPENSE, "CAT", LocalDate.now(), "CARD", TransactionScope.PERSONAL)
        );

        ParseResult result = ParseResult.builder()
                .transactions(txs)
                .dueDate(LocalDate.now().plusDays(10))
                .totalAmount(new BigDecimal("150.00"))
                .cardLastDigits("1234")
                .build();

        String rawText = "A".repeat(2000);

        int score = evaluator.evaluate(result, rawText);
        assertEquals(100, score);
    }

    @Test
    void evaluate_returnsLowScoreForBadExtraction() {
        QualityScoreConfig config = new QualityScoreConfig();
        config.setMinTransactions(3);
        config.setMinTextLength(800);
        config.setMaxGarbledPercent(5.0);

        ParseQualityEvaluator evaluator = new ParseQualityEvaluator(config);

        List<TransactionData> txs = List.of(
                new TransactionData("", null, TransactionType.EXPENSE, "CAT", null, "CARD", TransactionScope.PERSONAL),
                new TransactionData(" ", null, TransactionType.EXPENSE, "CAT", null, "CARD", TransactionScope.PERSONAL)
        );

        ParseResult result = ParseResult.builder()
                .transactions(txs)
                .dueDate(null)
                .totalAmount(BigDecimal.ZERO)
                .cardLastDigits(null)
                .build();

        String rawText = "x".repeat(100);

        int score = evaluator.evaluate(result, rawText);
        assertEquals(0, score);
    }

}
