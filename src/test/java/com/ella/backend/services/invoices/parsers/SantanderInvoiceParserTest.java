package com.ella.backend.services.invoices.parsers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ella.backend.enums.TransactionType;

class SantanderInvoiceParserTest {

    @Test
    void parsesHolderBlocksExpensesInstallmentsAndPayments() {
        String text = String.join("\n",
                "SANTANDER",
                "Total a Pagar R$ 44.815,95 Vencimento 20/12/2025",
                "",
                "ATILLA FERREGUETTI - 4258 XXXX XXXX 8854",
                "DETALHAMENTO DA FATURA",
                "DESPESAS",
                "17/11 RESTAURANTE VEGETARIA 43,75",
                "18/11 IFD*IFD*COMERCIO DE 120,90",
                "PAGAMENTOS E DÉBITOS CRÉDITOS",
                "19/11 DEB AUTOM DE FATURA EM C/ 934,83",
                "",
                "PABLO BONFANTE - 4258 XXXX XXXX 8830",
                "PARCELAMENTOS",
                "05/12 AIRBNB *HMF99EFWK9 01/10 369,48",
                "DESPESAS",
                "06/12 UBER TRIP 18,40",
                "RESUMO DA FATURA"
        );

        SantanderInvoiceParser parser = new SantanderInvoiceParser();
        assertTrue(parser.isApplicable(text));

        LocalDate dueDate = parser.extractDueDate(text);
        assertEquals(LocalDate.of(2025, 12, 20), dueDate);

        List<TransactionData> txs = parser.extractTransactions(text);
        assertNotNull(txs);
        assertEquals(5, txs.size());

        TransactionData t1 = txs.get(0);
        assertEquals("RESTAURANTE VEGETARIA", t1.description);
        assertEquals(TransactionType.EXPENSE, t1.type);
        assertEquals("Alimentação", t1.category);
        assertEquals(LocalDate.of(2025, 11, 17), t1.date);
        assertNotNull(t1.cardName);
        assertTrue(t1.cardName.contains("8854"));
        assertEquals("ATILLA FERREGUETTI", t1.cardholderName);
        assertEquals(0, t1.amount.compareTo(new BigDecimal("43.75")));

        TransactionData t2 = txs.get(1);
        assertEquals("IFD*IFD*COMERCIO DE", t2.description);
        assertEquals(TransactionType.EXPENSE, t2.type);
        assertEquals("iFood", t2.category);
        assertEquals(LocalDate.of(2025, 11, 18), t2.date);

        TransactionData t3 = txs.get(2);
        assertEquals("DEB AUTOM DE FATURA EM C/", t3.description);
        assertEquals(TransactionType.INCOME, t3.type);
        assertEquals("Pagamento", t3.category);
        assertEquals(LocalDate.of(2025, 11, 19), t3.date);
        assertEquals(0, t3.amount.compareTo(new BigDecimal("934.83")));

        TransactionData t4 = txs.get(3);
        assertEquals("AIRBNB *HMF99EFWK9", t4.description);
        assertEquals(TransactionType.EXPENSE, t4.type);
        assertEquals("Hospedagem", t4.category);
        assertEquals(LocalDate.of(2025, 12, 5), t4.date);
        assertNotNull(t4.cardName);
        assertTrue(t4.cardName.contains("8830"));
        assertEquals("PABLO BONFANTE", t4.cardholderName);
        assertEquals(1, t4.installmentNumber);
        assertEquals(10, t4.installmentTotal);
        assertEquals(0, t4.amount.compareTo(new BigDecimal("369.48")));

        TransactionData t5 = txs.get(4);
        assertEquals("UBER TRIP", t5.description);
        assertEquals(TransactionType.EXPENSE, t5.type);
        assertEquals("Transporte", t5.category);
        assertEquals(LocalDate.of(2025, 12, 6), t5.date);
        assertTrue(t5.cardName.contains("8830"));
    }

    @Test
    void extractsDueDateWhenYearIsMissing() {
        // Alguns layouts do Santander trazem "Vencimento 20/12" (sem ano).
        // Inferimos o ano a partir de qualquer data com ano presente no documento.
        String text = String.join("\n",
                "SANTANDER",
                "Emitido em: 19/12/2025",
                "Total a Pagar R$ 1.234,56 Vencimento 20/12",
                "ATILLA FERREGUETTI - 4258 XXXX XXXX 8854",
                "DESPESAS",
                "17/12 UBER TRIP 18,40"
        );

        SantanderInvoiceParser parser = new SantanderInvoiceParser();
        assertTrue(parser.isApplicable(text));

        LocalDate dueDate = parser.extractDueDate(text);
        assertEquals(LocalDate.of(2025, 12, 20), dueDate);
    }
}
