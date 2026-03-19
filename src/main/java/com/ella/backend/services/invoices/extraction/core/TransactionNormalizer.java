package com.ella.backend.services.invoices.extraction.core;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.ella.backend.enums.TransactionType;
import com.ella.backend.services.invoices.parsers.InvoiceParserStrategy;
import com.ella.backend.services.invoices.parsers.ItauInvoiceParser;
import com.ella.backend.services.invoices.parsers.TransactionData;

public final class TransactionNormalizer {

    private TransactionNormalizer() {
    }

    public static NormalizationResult normalize(InvoiceParserStrategy parser, LocalDate dueDate, List<TransactionData> transactions) {
        for (TransactionData tx : transactions) {
            if (tx == null) continue;
            tx.setDueDate(dueDate);
        }

        int before = transactions.size();
        int droppedAfterDueDate = 0;
        List<TransactionData> normalized = transactions;

        if (parser instanceof ItauInvoiceParser && dueDate != null && !transactions.isEmpty()) {
            List<TransactionData> filtered = new ArrayList<>(transactions.size());
            for (TransactionData tx : transactions) {
                if (tx == null) {
                    filtered.add(null);
                    continue;
                }
                if (tx.type == TransactionType.EXPENSE && tx.date != null && tx.date.isAfter(dueDate)) {
                    droppedAfterDueDate++;
                    continue;
                }
                filtered.add(tx);
            }
            normalized = filtered;
        }

        return new NormalizationResult(normalized, before, droppedAfterDueDate);
    }

    public static final class NormalizationResult {
        private final List<TransactionData> transactions;
        private final int beforeCount;
        private final int droppedAfterDueDate;

        private NormalizationResult(List<TransactionData> transactions, int beforeCount, int droppedAfterDueDate) {
            this.transactions = transactions;
            this.beforeCount = beforeCount;
            this.droppedAfterDueDate = droppedAfterDueDate;
        }

        public List<TransactionData> transactions() {
            return transactions;
        }

        public int beforeCount() {
            return beforeCount;
        }

        public int droppedAfterDueDate() {
            return droppedAfterDueDate;
        }
    }
}
