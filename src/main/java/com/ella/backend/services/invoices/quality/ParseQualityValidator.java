package com.ella.backend.services.invoices.quality;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import com.ella.backend.config.QualityScoreConfig;
import com.ella.backend.services.invoices.parsers.ParseResult;

import lombok.extern.slf4j.Slf4j;

/**
 * Valida a qualidade de um ParseResult usando configurações dinâmicas.
 */
@Slf4j
@Component
public class ParseQualityValidator {

    public boolean isValid(ParseResult result, QualityScoreConfig config) {
        if (result == null || config == null) {
            log.warn("[ParseQualityValidator] Result or config is null");
            return false;
        }

        log.debug("[ParseQualityValidator] Validating result with config: {}", config.getDescription());

        if (result.getQualityScore() < config.getMinScoreForAcceptance()) {
            log.warn("[ParseQualityValidator] REJECTED: Score {} < minimum {}",
                    result.getQualityScore(), config.getMinScoreForAcceptance());
            return false;
        }

        if (result.getTransactions() == null || result.getTransactions().isEmpty()) {
            log.warn("[ParseQualityValidator] REJECTED: No transactions found");
            return false;
        }

        if (result.getTransactions().size() < config.getMinTransactions()) {
            log.warn("[ParseQualityValidator] REJECTED: Too few transactions: {} < {}",
                    result.getTransactions().size(), config.getMinTransactions());
            return false;
        }

        if (result.getDueDate() == null) {
            log.warn("[ParseQualityValidator] REJECTED: No due date found");
            return false;
        }

        if (result.getTotalAmount() == null || result.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("[ParseQualityValidator] REJECTED: No total amount or is zero");
            return false;
        }

        log.info("[ParseQualityValidator] ACCEPTED: Result is valid (score: {}, transactions: {})",
                result.getQualityScore(), result.getTransactions().size());

        return true;
    }

    public boolean isHighQuality(ParseResult result, QualityScoreConfig config) {
        if (result == null || config == null) {
            return false;
        }

        boolean isHigh = result.getQualityScore() >= config.getMinScoreForHighQuality();

        log.debug("[ParseQualityValidator] Quality check: score={}, threshold={}, isHigh={}",
                result.getQualityScore(), config.getMinScoreForHighQuality(), isHigh);

        return isHigh;
    }

    public String getRejectReason(ParseResult result, QualityScoreConfig config) {
        if (result == null) {
            return "ParseResult is null";
        }
        if (config == null) {
            return "QualityScoreConfig is null";
        }

        if (result.getQualityScore() < config.getMinScoreForAcceptance()) {
            return String.format("Score too low: %d < %d",
                    result.getQualityScore(), config.getMinScoreForAcceptance());
        }

        if (result.getTransactions() == null || result.getTransactions().isEmpty()) {
            return "No transactions found";
        }

        if (result.getTransactions().size() < config.getMinTransactions()) {
            return String.format("Too few transactions: %d < %d",
                    result.getTransactions().size(), config.getMinTransactions());
        }

        if (result.getDueDate() == null) {
            return "No due date found";
        }

        if (result.getTotalAmount() == null || result.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return "No total amount or is zero";
        }

        return "Unknown reason";
    }
}
