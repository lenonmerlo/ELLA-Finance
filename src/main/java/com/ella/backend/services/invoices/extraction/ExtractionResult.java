package com.ella.backend.services.invoices.extraction;

import java.util.List;

import com.ella.backend.services.invoices.parsers.ParseResult;
import com.ella.backend.services.invoices.parsers.TransactionData;

public record ExtractionResult(
        ParseResult parseResult,
        String rawText,
        String source,
        boolean ocrAttempted,
        String fallbackDecision
) {
    public ExtractionResult(ParseResult parseResult, String rawText, String source, boolean ocrAttempted) {
        this(parseResult, rawText, source, ocrAttempted, null);
    }

    public List<TransactionData> transactions() {
        if (parseResult == null || parseResult.getTransactions() == null) return List.of();
        return parseResult.getTransactions();
    }
}
