package com.ella.backend.services.invoices.parsers;

/**
 * Optional addon interface for parsers that can benefit from the original PDF bytes.
 *
 * This does NOT replace {@link InvoiceParserStrategy}; it is only used by the pipeline
 * for a small, isolated branch when the chosen parser supports PDF-aware parsing.
 */
public interface PdfAwareInvoiceParser {
    ParseResult parseWithPdf(byte[] pdfBytes, String extractedText);
}
