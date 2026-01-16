package com.ella.backend.services.invoices;

/**
 * Thrown when an invoice extraction/parsing result is considered too low-quality to accept.
 */
public class InvoiceParsingException extends IllegalArgumentException {

    public InvoiceParsingException(String message) {
        super(message);
    }

    public InvoiceParsingException(String message, Throwable cause) {
        super(message, cause);
    }
}
