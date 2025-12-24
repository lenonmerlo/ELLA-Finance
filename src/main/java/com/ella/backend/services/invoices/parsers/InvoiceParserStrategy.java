package com.ella.backend.services.invoices.parsers;

import java.time.LocalDate;
import java.util.List;

public interface InvoiceParserStrategy {
    boolean isApplicable(String text);

    LocalDate extractDueDate(String text);

    List<TransactionData> extractTransactions(String text);
}
