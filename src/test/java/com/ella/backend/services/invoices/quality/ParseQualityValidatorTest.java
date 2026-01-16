package com.ella.backend.services.invoices.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ella.backend.config.QualityScoreConfig;
import com.ella.backend.enums.TransactionScope;
import com.ella.backend.enums.TransactionType;
import com.ella.backend.services.invoices.parsers.ParseResult;
import com.ella.backend.services.invoices.parsers.TransactionData;

class ParseQualityValidatorTest {

    @Test
    void isValid_rejectsWhenScoreBelowConfigThreshold() {
        QualityScoreConfig config = new QualityScoreConfig();
        config.setMinScoreForAcceptance(90);
        config.setMinTransactions(3);

        ParseResult result = ParseResult.builder()
                .qualityScore(85)
                .dueDate(LocalDate.now().plusDays(10))
                .totalAmount(new BigDecimal("100.00"))
                .transactions(List.of(
                        new TransactionData("A", new BigDecimal("10.00"), TransactionType.EXPENSE, "CAT", LocalDate.now(), "CARD", TransactionScope.PERSONAL),
                        new TransactionData("B", new BigDecimal("20.00"), TransactionType.EXPENSE, "CAT", LocalDate.now(), "CARD", TransactionScope.PERSONAL),
                        new TransactionData("C", new BigDecimal("30.00"), TransactionType.EXPENSE, "CAT", LocalDate.now(), "CARD", TransactionScope.PERSONAL)))
                .build();

        ParseQualityValidator validator = new ParseQualityValidator();
        assertFalse(validator.isValid(result, config));
        assertEquals("Score too low: 85 < 90", validator.getRejectReason(result, config));
    }

    @Test
    void isValid_acceptsWhenMeetsConfigThresholds() {
        QualityScoreConfig config = new QualityScoreConfig();
        config.setMinScoreForAcceptance(50);
        config.setMinTransactions(3);

        ParseResult result = ParseResult.builder()
                .qualityScore(85)
                .dueDate(LocalDate.now().plusDays(10))
                .totalAmount(new BigDecimal("100.00"))
                .transactions(List.of(
                        new TransactionData("A", new BigDecimal("10.00"), TransactionType.EXPENSE, "CAT", LocalDate.now(), "CARD", TransactionScope.PERSONAL),
                        new TransactionData("B", new BigDecimal("20.00"), TransactionType.EXPENSE, "CAT", LocalDate.now(), "CARD", TransactionScope.PERSONAL),
                        new TransactionData("C", new BigDecimal("30.00"), TransactionType.EXPENSE, "CAT", LocalDate.now(), "CARD", TransactionScope.PERSONAL)))
                .build();

        ParseQualityValidator validator = new ParseQualityValidator();
        assertTrue(validator.isValid(result, config));
    }
}
