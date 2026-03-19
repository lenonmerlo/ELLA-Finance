package com.ella.backend.services.invoices.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ella.backend.enums.TransactionType;
import com.ella.backend.services.invoices.extraction.core.TransactionNormalizer;
import com.ella.backend.services.invoices.parsers.InvoiceParserStrategy;
import com.ella.backend.services.invoices.parsers.ItauInvoiceParser;
import com.ella.backend.services.invoices.parsers.TransactionData;

class TransactionNormalizerTest {

    @Test
    void normalizerSetsDueDateForAllNonNullTransactions() {
        LocalDate dueDate = LocalDate.of(2026, 1, 20);
        List<TransactionData> transactions = new ArrayList<>();
        transactions.add(tx("Compra A", "100.00", TransactionType.EXPENSE, LocalDate.of(2026, 1, 10)));
        transactions.add(null);
        transactions.add(tx("Compra B", "50.00", TransactionType.EXPENSE, LocalDate.of(2026, 1, 11)));

        TransactionNormalizer.NormalizationResult result = TransactionNormalizer.normalize(new NonItauParser(), dueDate, transactions);

        assertEquals(3, result.transactions().size());
        assertEquals(0, result.droppedAfterDueDate());
        assertNotNull(result.transactions().get(0).dueDate);
        assertNull(result.transactions().get(1));
        assertNotNull(result.transactions().get(2).dueDate);
        assertEquals(dueDate, result.transactions().get(0).dueDate);
        assertEquals(dueDate, result.transactions().get(2).dueDate);
    }

    @Test
    void itauDropsOnlyExpenseAfterDueDate() {
        LocalDate dueDate = LocalDate.of(2026, 1, 20);
        List<TransactionData> transactions = new ArrayList<>();

        transactions.add(tx("Despesa antes", "100.00", TransactionType.EXPENSE, LocalDate.of(2026, 1, 19)));
        transactions.add(tx("Despesa depois", "200.00", TransactionType.EXPENSE, LocalDate.of(2026, 1, 21)));
        transactions.add(tx("Receita depois", "300.00", TransactionType.INCOME, LocalDate.of(2026, 1, 22)));
        transactions.add(null);

        TransactionNormalizer.NormalizationResult result = TransactionNormalizer.normalize(new ItauInvoiceParser(), dueDate, transactions);

        assertEquals(4, result.beforeCount());
        assertEquals(1, result.droppedAfterDueDate());
        assertEquals(3, result.transactions().size());
        assertEquals("Despesa antes", result.transactions().get(0).description);
        assertEquals("Receita depois", result.transactions().get(1).description);
        assertNull(result.transactions().get(2));
        assertEquals(dueDate, result.transactions().get(0).dueDate);
        assertEquals(dueDate, result.transactions().get(1).dueDate);
    }

    private static TransactionData tx(String description, String amount, TransactionType type, LocalDate date) {
        return new TransactionData(
                description,
                new BigDecimal(amount),
                type,
                null,
                date,
                null,
                null
        );
    }

    private static final class NonItauParser implements InvoiceParserStrategy {
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
            return List.of();
        }
    }
}
