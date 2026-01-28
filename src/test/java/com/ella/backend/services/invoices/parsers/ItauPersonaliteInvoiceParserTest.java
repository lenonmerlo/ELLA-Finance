package com.ella.backend.services.invoices.parsers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

class ItauPersonaliteInvoiceParserTest {

    @Test
    void acceptsPersonaliteEvenWhenItauLabelIsMissingFromText() {
        // Simula extração PDFBox "garbled" onde o header com "ITAU" pode não aparecer,
        // mas as âncoras do layout ainda existem.
        String text = String.join("\n",
                "Resumo da fatura em R$",
                "Lanamentos no carto (final 8578)",
                "Lanamentos: compras e saques",
                "17/11 ALLIANZ SEGU*09 de 10 188,39",
                "Total dos lanamentos atuais 3.760,96");

        ItauPersonaliteInvoiceParser parser = new ItauPersonaliteInvoiceParser();
        assertTrue(parser.isApplicable(text));
    }

    @Test
    void isApplicableWhenPersonaliteAndHasMultipleCards() {
        String text = String.join("\n",
                "Itau Personnalitê",
                "Resumo da fatura",
                "Vencimento: 21/11/2025",
                "",
                "Lançamentos no cartão (final 8578)",
                "Lançamentos: compras e saques",
                "17/11 UBER TRIP 18,40");

        ItauPersonaliteInvoiceParser parser = new ItauPersonaliteInvoiceParser();
        assertTrue(parser.isApplicable(text));
    }

    @Test
    void rejectsNubankInvoiceEvenIfItMentionsInstallments() {
        String nubankText = String.join("\n",
                "Esta é a sua fatura de dezembro, no valor de R$ 1.107,60",
                "Data de vencimento: 12 DEZ 2025",
                "Período vigente: 05 NOV a 05 DEZ",
                "Limite total do cartão de crédito: R$ 1.300,00",
                "Compras parceladas - próximas faturas");

        ItauPersonaliteInvoiceParser parser = new ItauPersonaliteInvoiceParser();
        assertFalse(parser.isApplicable(nubankText), "Personalité parser deve REJEITAR Nubank invoice");
    }

    @Test
    void rejectsRegularItauInvoice() {
        String itauRegularText = String.join("\n",
                "ITAU UNIBANCO S.A.",
                "FATURA DO CARTAO DE CREDITO",
                "Período: 01/11/2025 a 30/11/2025",
                "Vencimento: 15/12/2025",
                "Saldo anterior: R$ 0,00",
                "Compras parceladas - próximas faturas");

        ItauPersonaliteInvoiceParser parser = new ItauPersonaliteInvoiceParser();
        assertFalse(parser.isApplicable(itauRegularText), "Personalité parser deve REJEITAR Itaú Regular invoice");
    }

    @Test
    void acceptsPersonaliteInvoiceWithAllThreeMarkers() {
        String personaliteText = String.join("\n",
                "ITAU PERSONNALITE",
                "MASTERCARD",
                "Com vencimento em: 01/12/2025",
                "Lançamentos no cartão (final 8578)",
                "Lançamentos: compras e saques",
                "17/11 ALLIANZ SEGU*09 de 10 188,39",
                "Compras parceladas - próximas faturas");

        ItauPersonaliteInvoiceParser parser = new ItauPersonaliteInvoiceParser();
        assertTrue(parser.isApplicable(personaliteText), "Personalité parser deve ACEITAR Personalité invoice");
    }

    @Test
    void acceptsPersonaliteWithUppercase() {
        String personaliteUppercase = String.join("\n",
                "ITAU PERSONNALITE",
                "MASTERCARD",
                "Com vencimento em: 01/12/2025",
                "Lançamentos no cartão (final 8578)",
                "Lançamentos: compras e saques",
                "17/11 ALLIANZ SEGU*09 de 10 188,39",
                "Compras parceladas - próximas faturas");

        ItauPersonaliteInvoiceParser parser = new ItauPersonaliteInvoiceParser();
        assertTrue(parser.isApplicable(personaliteUppercase), "Personalité parser deve ACEITAR com MAIÚSCULO");
    }

    @Test
    void acceptsPersonaliteWithMixedCase() {
        String personaliteMixed = String.join("\n",
                "Itau Personnalitê",
                "MasterCard",
                "Com vencimento em: 01/12/2025",
                "Lançamentos no cartão (final 8578)",
                "Lançamentos: compras e saques",
                "17/11 ALLIANZ SEGU*09 de 10 188,39",
                "Compras parceladas - próximas faturas");

        ItauPersonaliteInvoiceParser parser = new ItauPersonaliteInvoiceParser();
        assertTrue(parser.isApplicable(personaliteMixed), "Personalité parser deve ACEITAR com case misto");
    }

    @Test
    void ignoresInstallmentsFutureSection() {
        String text = String.join("\n",
                "Itau Personnalitê",
                "Resumo da fatura",
                "Vencimento: 21/11/2025",
                "",
                "Lançamentos: compras e saques",
                "17/11 UBER TRIP 18,40",
                "18/11 IFD*IFD*COMERCIO DE 120,90",
                "",
                // PDFBox pode quebrar "próximas" em duas linhas (ex.: "pr" + "ximas").
                "Compras parceladas - pr",
                "ximas faturas",
                "22/01 BT SHOP VITORI 11/12 482,00",
                "23/01 OUTRA LOJA 01/05 100,00",
                "",
                "Encargos cobrados nesta fatura");

        ItauPersonaliteInvoiceParser parser = new ItauPersonaliteInvoiceParser();
        List<TransactionData> txs = parser.extractTransactions(text);
        assertNotNull(txs);
        assertEquals(2, txs.size());
    }

    @Test
    void ignoresDateOnlyAndPeriodRangeNoiseLines() {
        String text = String.join("\n",
                "Resumo da fatura em R$",
                "Vencimento: 01/12/2025",
                "Previsão prox. Fechamento: 24/12/2025.",
                "(01/12 a 31/12)",
                "Lanamentos no carto (final 8578)",
                "19/05 COS SERVICOSMEDIC07/10 500,00",
                "Compras parceladas - pr",
                "ximas faturas",
                "19/05 COS SERVICOSMEDIC08/10 500,00",
                "Total dos lanamentos atuais 3.760,96");

        ItauPersonaliteInvoiceParser parser = new ItauPersonaliteInvoiceParser();
        assertTrue(parser.isApplicable(text));

        List<TransactionData> txs = parser.extractTransactions(text);
        assertNotNull(txs);
        assertEquals(1, txs.size());
        assertEquals(new BigDecimal("500.00"), txs.get(0).amount);
    }

    @Test
    void extractsMoneyAtEndDespite09de10Noise() {
        String text = String.join("\n",
                "Itau Personnalitê",
                "Resumo da fatura",
                "Vencimento: 21/11/2025",
                "",
                "Lançamentos: compras e saques",
                "17/11 ALLIANZ SEGUROS 09de10 188,39");

        ItauPersonaliteInvoiceParser parser = new ItauPersonaliteInvoiceParser();
        List<TransactionData> txs = parser.extractTransactions(text);
        assertNotNull(txs);
        assertEquals(1, txs.size());

        TransactionData t = txs.get(0);
        assertTrue(t.description.toLowerCase().contains("allianz"));
        assertEquals(new BigDecimal("188.39"), t.amount);
        assertEquals(LocalDate.of(2025, 11, 17), t.date);
    }

    @Test
    void stripsLeadingSymbolBeforeDate() {
        String text = String.join("\n",
                "Itau Personnalitê",
                "Resumo da fatura",
                "Vencimento: 21/11/2025",
                "",
                "Lançamentos: compras e saques",
                "☎ 17/11 UBER TRIP 18,40");

        ItauPersonaliteInvoiceParser parser = new ItauPersonaliteInvoiceParser();
        List<TransactionData> txs = parser.extractTransactions(text);
        assertNotNull(txs);
        assertEquals(1, txs.size());
        assertEquals(LocalDate.of(2025, 11, 17), txs.get(0).date);
    }

    @Test
    void setsCardNamePerCardSection() {
        String text = String.join("\n",
                "Itau Personnalitê",
                "Mastercard",
                "Resumo da fatura",
                "Vencimento: 21/11/2025",
                "",
                "Lançamentos no cartão (final 8578)",
                "17/11 UBER TRIP 18,40",
                "",
                "Lançamentos no cartão (final 2673)",
                "18/11 AMAZON 120,90");

        ItauPersonaliteInvoiceParser parser = new ItauPersonaliteInvoiceParser();
        List<TransactionData> txs = parser.extractTransactions(text);
        assertNotNull(txs);
        assertEquals(2, txs.size());

        TransactionData t1 = txs.get(0);
        TransactionData t2 = txs.get(1);

        assertTrue(t1.cardName.contains("final 8578"));
        assertTrue(t2.cardName.contains("final 2673"));
        assertTrue(t1.cardName.toLowerCase().contains("mastercard"));
        assertTrue(t2.cardName.toLowerCase().contains("mastercard"));
    }

    @Test
    void usesCategoryFromSecondLineWhenPresent() {
        String text = String.join("\n",
                "Itau Personnalitê",
                "Resumo da fatura",
                "Vencimento: 21/11/2025",
                "",
                "Lançamentos: compras e saques",
                "17/11 FARMACIA 18,40",
                "SAÚDE.SAO PAULO");

        ItauPersonaliteInvoiceParser parser = new ItauPersonaliteInvoiceParser();
        List<TransactionData> txs = parser.extractTransactions(text);
        assertNotNull(txs);
        assertEquals(1, txs.size());
        assertEquals("SAÚDE", txs.get(0).category);
    }

    @Test
    void dedupesFutureInstallmentDuplicatesByKeepingLowestInstallmentNumber() {
        String text = String.join("\n",
                "Itau Personnalitê",
                "Mastercard",
                "Com vencimento em: 01/12/2025",
                "Lançamentos no cartão (final 8578)",
                "Lançamentos: compras e saques",
                // fatura atual
                "19/05 COS SERVICOSMEDIC07/10 500,00",
                // próximas faturas (duplicado com parcela incrementada)
                "19/05 COS SERVICOSMEDIC08/10 500,00",
                "Total dos lancamentos atuais 3.760,96");

        ItauPersonaliteInvoiceParser parser = new ItauPersonaliteInvoiceParser();
        List<TransactionData> txs = parser.extractTransactions(text);
        assertNotNull(txs);
        assertEquals(1, txs.size());
        assertEquals(7, txs.get(0).installmentNumber);
        assertEquals(10, txs.get(0).installmentTotal);
        assertEquals(new BigDecimal("500.00"), txs.get(0).amount);
    }

    @Test
    void extractsTransactionsEvenWhenTheyAppearBeforeLaunchesAnchors() {
        // Reproduz o caso real: PDFBox pode colocar a lista de transações antes de "Lançamentos: compras e saques".
        String text = String.join("\n",
                "Resumo da fatura em R$",
                "Vencimento: 01/12/2025",
                "Continua...",
                "Lanamentos no carto (final 8578)",
                "19/05 COS SERVICOSMEDIC07/10 500,00",
                "Lanamentos atuais 3.760,96",
                "Lanamentos: compras e saques",
                "Compras parceladas - próximas faturas",
                "19/05 COS SERVICOSMEDIC08/10 500,00");

        ItauPersonaliteInvoiceParser parser = new ItauPersonaliteInvoiceParser();
        assertTrue(parser.isApplicable(text));

        List<TransactionData> txs = parser.extractTransactions(text);
        assertNotNull(txs);
        assertEquals(1, txs.size());
        assertEquals(new BigDecimal("500.00"), txs.get(0).amount);
    }
}
