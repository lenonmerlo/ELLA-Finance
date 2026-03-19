package com.ella.backend.services.invoices.extraction;

import java.time.LocalDate;
import java.util.function.Function;
import java.util.function.Predicate;

import com.ella.backend.services.invoices.parsers.ParseResult;

public interface ParserParent {
    ParseResult parse(
            byte[] pdfBytes,
            String text,
            LocalDate dueDateFromRequest,
            Function<String, LocalDate> dueDateFallbackExtractor,
            Predicate<String> unsupportedInvoiceDetector
    );
}
