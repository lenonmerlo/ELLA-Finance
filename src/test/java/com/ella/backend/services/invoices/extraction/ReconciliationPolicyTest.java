package com.ella.backend.services.invoices.extraction;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ella.backend.enums.TransactionType;
import com.ella.backend.services.invoices.extraction.core.ReconciliationPolicy;
import com.ella.backend.services.invoices.parsers.TransactionData;

class ReconciliationPolicyTest {

    @Test
    void qualityRetryTriggersWhenManyDatesAreMissing() {
        List<TransactionData> txs = List.of(
                tx("COMPRA NORMAL", "100.00", TransactionType.EXPENSE, null),
                tx("OUTRA COMPRA", "90.00", TransactionType.EXPENSE, null),
                tx("MERCADO", "80.00", TransactionType.EXPENSE, LocalDate.of(2026, 1, 5))
        );

        assertTrue(ReconciliationPolicy.shouldRetryWithOcrForQuality(txs));
    }

    @Test
    void missingTransactionsRetryTriggersOnLowCoverageAgainstExpectedTotal() {
        String text = String.join("\n",
                "Valor da fatura: R$ 1.000,00",
                "Vencimento 20/01/2026"
        );

        List<TransactionData> txs = List.of(
                tx("COMPRA", "900.00", TransactionType.EXPENSE, LocalDate.of(2026, 1, 2))
        );

        assertTrue(ReconciliationPolicy.shouldRetryDueToMissingTransactions(txs, text));
    }

    @Test
    void ocrMissingTransactionsComparisonPrefersCloserAndNotSmallerSet() {
        BigDecimal expected = new BigDecimal("1000.00");

        List<TransactionData> original = List.of(
                tx("A", "450.00", TransactionType.EXPENSE, LocalDate.of(2026, 1, 2)),
                tx("B", "450.00", TransactionType.EXPENSE, LocalDate.of(2026, 1, 3))
        );

        List<TransactionData> ocrWorse = List.of(
                tx("A", "200.00", TransactionType.EXPENSE, LocalDate.of(2026, 1, 2)),
                tx("B", "200.00", TransactionType.EXPENSE, LocalDate.of(2026, 1, 3))
        );

        List<TransactionData> ocrBetter = List.of(
                tx("A", "450.00", TransactionType.EXPENSE, LocalDate.of(2026, 1, 2)),
                tx("B", "450.00", TransactionType.EXPENSE, LocalDate.of(2026, 1, 3)),
                tx("C", "90.00", TransactionType.EXPENSE, LocalDate.of(2026, 1, 4))
        );

        assertFalse(ReconciliationPolicy.isOcrResultBetterForMissingTransactions(ocrWorse, original, expected));
        assertTrue(ReconciliationPolicy.isOcrResultBetterForMissingTransactions(ocrBetter, original, expected));
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
}
