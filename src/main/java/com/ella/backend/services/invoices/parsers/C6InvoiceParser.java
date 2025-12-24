package com.ella.backend.services.invoices.parsers;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

public class C6InvoiceParser implements InvoiceParserStrategy {

    @Override
    public boolean isApplicable(String text) {
        return false;
    }

    @Override
    public LocalDate extractDueDate(String text) {
        return null;
    }

    @Override
    public List<TransactionData> extractTransactions(String text) {
        return Collections.emptyList();
    }
}
