package com.ella.backend.services.invoices.parsers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ella.backend.enums.TransactionType;

class SantanderInvoiceParserTest {

    @Test
    void rejectsPersonaliteAnchorsEvenWithoutItauWordPresent() {
        // Simula texto extraído "garbled" que pode perder o header "ITAU",
        // mas ainda contém âncoras fortes do layout de fatura Itaú.
        String garbledPersonalite = String.join("\n",
                "Com vencimento em: 01/12/2025",
                "Resumo da fatura em R$",
                "Lanamentos no carto (final 8578)",
                "Lanamentos: compras e saques",
                "Total dos lanamentos atuais 3.760,96");

        SantanderInvoiceParser parser = new SantanderInvoiceParser();
        assertFalse(parser.isApplicable(garbledPersonalite));
    }

    @Test
    void rejectsPersonaliteWithUppercase() {
    String personaliteUppercase = String.join("\n",
        "ITAU PERSONALITE",
        "MASTERCARD",
        "Com vencimento em: 01/12/2025",
        "Total a Pagar R$ 3.760,96 Vencimento 01/12/2025",
        "Lançamentos no cartão (final 8578)",
        "Lançamentos: compras e saques");

    SantanderInvoiceParser parser = new SantanderInvoiceParser();
    assertFalse(parser.isApplicable(personaliteUppercase),
        "Santander parser deve REJEITAR Personalité (mesmo com maiúsculo)");
    }

    @Test
    void rejectsPersonaliteWithMixedCase() {
    String personaliteMixed = String.join("\n",
        "Itaú Personalité",
        "MasterCard",
        "Com vencimento em: 01/12/2025",
        "Total a Pagar R$ 3.760,96 Vencimento 01/12/2025",
        "Lançamentos no cartão (final 8578)",
        "Lançamentos: compras e saques");

    SantanderInvoiceParser parser = new SantanderInvoiceParser();
    assertFalse(parser.isApplicable(personaliteMixed),
        "Santander parser deve REJEITAR Personalité (mesmo com case misto)");
    }

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
        // Automatic debit lines are informational and should be ignored.
        assertEquals(4, txs.size());

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
        assertEquals("AIRBNB *HMF99EFWK9", t3.description);
        assertEquals(TransactionType.EXPENSE, t3.type);
        assertEquals("Hospedagem", t3.category);
        assertEquals(LocalDate.of(2025, 12, 5), t3.date);
        assertNotNull(t3.cardName);
        assertTrue(t3.cardName.contains("8830"));
        assertEquals("PABLO BONFANTE", t3.cardholderName);
        assertEquals(1, t3.installmentNumber);
        assertEquals(10, t3.installmentTotal);
        assertEquals(0, t3.amount.compareTo(new BigDecimal("369.48")));

        TransactionData t4 = txs.get(3);
        assertEquals("UBER TRIP", t4.description);
        assertEquals(TransactionType.EXPENSE, t4.type);
        assertEquals("Transporte", t4.category);
        assertEquals(LocalDate.of(2025, 12, 6), t4.date);
        assertTrue(t4.cardName.contains("8830"));
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

    @Test
    void parsesTransactionsWhenCompraColumnPrefixesDigits() {
        // Some Santander PDFs add a leading "Compra" column before the date.
        // When copied/extracted, symbols like "@" and "))))" may appear as digits like "2" or "3".
        String text = String.join("\n",
                "SANTANDER",
                "Total a Pagar R$ 2.980,74 Vencimento 20/12/2025",
                "ATILLA FERREGUETTI - 4258 XXXX XXXX 8854",
                "DESPESAS",
                "2 12/11 P9ESPACOLASERES 160,42",
                "3 13/11 RESTAURANTE VEGETARIA 100,00",
                "3 15/11 CAMBURI RESTAURANTES 296,10"
        );

        SantanderInvoiceParser parser = new SantanderInvoiceParser();
        assertTrue(parser.isApplicable(text));

        List<TransactionData> txs = parser.extractTransactions(text);
        assertNotNull(txs);
        assertEquals(3, txs.size());

        TransactionData t1 = txs.get(0);
        assertEquals("P9ESPACOLASERES", t1.description);
        assertEquals(TransactionType.EXPENSE, t1.type);
        assertEquals(LocalDate.of(2025, 11, 12), t1.date);
        assertEquals(0, t1.amount.compareTo(new BigDecimal("160.42")));

        TransactionData t2 = txs.get(1);
        assertEquals("RESTAURANTE VEGETARIA", t2.description);
        assertEquals(TransactionType.EXPENSE, t2.type);
        assertEquals(LocalDate.of(2025, 11, 13), t2.date);
        assertEquals(0, t2.amount.compareTo(new BigDecimal("100.00")));

        TransactionData t3 = txs.get(2);
        assertEquals("CAMBURI RESTAURANTES", t3.description);
        assertEquals(TransactionType.EXPENSE, t3.type);
        assertEquals(LocalDate.of(2025, 11, 15), t3.date);
        assertEquals(0, t3.amount.compareTo(new BigDecimal("296.10")));
    }

    @Test
    void parsesRefundsAsIncomeWhenAmountIsNegativeEvenWithoutEstornoKeyword() {
        // Negative amounts are refunds/credits in Santander invoices. They must be typed as INCOME
        // so downstream totals subtract them (TransactionData amount is stored as abs()).
        String text = String.join("\n",
                "SANTANDER",
                "Total a Pagar R$ 44.815,95 Vencimento 20/12/2025",
                "ATILLA FERREGUETTI - 4258 XXXX XXXX 8854",
                "DESPESAS",
                "30/11 AIRBNB * HM39HDCYZW 7.464,87",
                // U+2212 minus sign (common in PDFs)
                "30/11 AIRBNB * HM39HDCYZW −7.464,87",
                // Standard hyphen minus, with extra token before the amount
                "17/11 EST ANUIDADE DIFERENCIADA T -43,75"
        );

        SantanderInvoiceParser parser = new SantanderInvoiceParser();
        assertTrue(parser.isApplicable(text));

        List<TransactionData> txs = parser.extractTransactions(text);
        assertNotNull(txs);
        assertEquals(3, txs.size());

        TransactionData purchase = txs.get(0);
        assertEquals("AIRBNB * HM39HDCYZW", purchase.description);
        assertEquals(TransactionType.EXPENSE, purchase.type);
        assertEquals(0, purchase.amount.compareTo(new BigDecimal("7464.87")));

        TransactionData refund = txs.get(1);
        assertEquals("AIRBNB * HM39HDCYZW", refund.description);
        assertEquals(TransactionType.INCOME, refund.type);
        assertEquals(0, refund.amount.compareTo(new BigDecimal("7464.87")));

        TransactionData annuityRefund = txs.get(2);
        assertEquals("EST ANUIDADE DIFERENCIADA T", annuityRefund.description);
        assertEquals(TransactionType.INCOME, annuityRefund.type);
        assertEquals(0, annuityRefund.amount.compareTo(new BigDecimal("43.75")));
    }

    @Test
    void ignoresAutomaticDebitInvoicePaymentLines() {
        String text = String.join("\n",
                "SANTANDER",
                "Total a Pagar R$ 1.234,56 Vencimento 20/12/2025",
                "ATILLA FERREGUETTI - 4258 XXXX XXXX 8854",
                "PAGAMENTOS E DÉBITOS CRÉDITOS",
                "21/11 DEB AUTOM DE FATURA EM C/ -7.131,52",
                "DESPESAS",
                "17/11 RESTAURANTE VEGETARIA 43,75"
        );

        SantanderInvoiceParser parser = new SantanderInvoiceParser();
        List<TransactionData> txs = parser.extractTransactions(text);
        assertEquals(1, txs.size());
        assertEquals("RESTAURANTE VEGETARIA", txs.get(0).description);
    }
}
