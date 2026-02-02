package com.ella.backend.services.invoices.parsers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class InvoiceParserSelectorTest {

    @Test
    void selectsItauForItauTextEvenIfSantanderPatternsCouldMatchTotalToPay() {
        String text = String.join("\n",
                "Banco Itaú",
                "Itaucard",
                "Total a Pagar R$ 3.692,62 Vencimento 22/12/2025",
                "",
                "Pagamentos efetuados",
                "21/11/2025 PAGAMENTO EFETUADO -100,00",
                "",
                "Lançamentos: compras e saques",
                "17/11 UBER TRIP 18,40"
        );

        InvoiceParserFactory factory = new InvoiceParserFactory("http://localhost:8000");
        InvoiceParserSelector.Selection selection = InvoiceParserSelector.selectBest(factory.getParsers(), text);

        assertEquals("ItauInvoiceParser", selection.chosen().parser().getClass().getSimpleName());
        assertTrue(selection.chosen().applicable());
        assertTrue(selection.chosen().txCount() > 0);
    }

    @Test
    void selectsItauRegularWhenPersonaliteWouldOtherwiseFalsePositiveOnGenericMarkers() {
        // Regression: "Resumo/Lançamentos atuais/Total dos lançamentos atuais" aparecem em Itaú regular.
        // O Personalité não deve aceitar esse layout sem "personalite" e sem "final 1234".
        String text = String.join("\n",
                "ITAU UNIBANCO S.A.",
                "Resumo da fatura em R$",
                "Lançamentos atuais 2.005,92",
                "Total dos lançamentos atuais 2.005,92",
                "",
                "Pagamentos efetuados",
                "21/11 PAGAMENTO DEB AUTOMATIC -3.692,62",
                "Total dos pagamentos -3.692,62",
                "",
                "Lançamentos: compras e saques",
                "15/10 CLINICA SCHUNK 02/05 720,00",
                "28/08 OTICA PARIS F6 04/10 83,92",
                "21/08 CLINICA SCHUNK 04/05 720,00",
                "22/01 BT SHOP VITORI 11/12 482,00",
                "",
                "Compras parceladas - próximas faturas",
                "22/01 BT SHOP VITORI 12/12 482,00");

        InvoiceParserFactory factory = new InvoiceParserFactory("http://localhost:8000");
        InvoiceParserSelector.Selection selection = InvoiceParserSelector.selectBest(factory.getParsers(), text);

        assertEquals("ItauInvoiceParser", selection.chosen().parser().getClass().getSimpleName());
        assertTrue(selection.chosen().applicable());
        assertEquals(4, selection.chosen().txCount());
    }

    @Test
    void selectsItauRegularEvenWhenInvoiceContainsInfiniteMarker() {
        // Regression: regular Itaú invoices can contain "Infinite" but are not Personalité.
        String text = String.join("\n",
                "ITAU UNIBANCO S.A.",
                "Resumo da fatura em R$",
                "Total desta fatura 2.005,92",
                "Pagamento mínimo:",
                "R$ 200,59",
                "Vencimento: 22/12/2025",
                "Infinite",
                "Pagamentos efetuados",
                "21/11 PAGAMENTO DEB AUTOMATIC -3.692,62",
                "Lançamentos: compras e saques",
                "15/10 CLINICA SCHUNK 02/05 720,00",
                "28/08 OTICA PARIS F6 04/10 83,92",
                "21/08 CLINICA SCHUNK 04/05 720,00",
                "22/01 BT SHOP VITORI 11/12 482,00",
                "Compras parceladas - próximas faturas",
                "22/01 BT SHOP VITORI 12/12 482,00");

        InvoiceParserFactory factory = new InvoiceParserFactory("http://localhost:8000");
        InvoiceParserSelector.Selection selection = InvoiceParserSelector.selectBest(factory.getParsers(), text);

        assertEquals("ItauInvoiceParser", selection.chosen().parser().getClass().getSimpleName());
        assertTrue(selection.chosen().applicable());
        assertEquals(4, selection.chosen().txCount());
    }

    @Test
    void selectsBradescoForBradescoTextEvenThoughMercadoPagoRegexCouldMatchLines() {
        String text = String.join("\n",
                "BRADESCO",
                "Titular: MARIANA OLIVEIRA DE CASTRO",
                "Cartão: VISA AETERNUM",
                "Total da fatura: R$ 13.646,35",
                "Vencimento: 25/12/2025",
                "",
                "LANÇAMENTOS",
                "Data  Histórico de Lançamentos                     Valor",
                "27/10 CTCE FORTALEZA CE P/1                    19.813,33",
                "12/11 CUSTO TRANS. EXTERIOR-IOF                   6,71",
                "18/03 UNIMED LITORAL                           306,88",
                "01/12 BRADESCO AUTO                             50,00",
                "05/12 PAYGOAL                                  -10,00",
                "",
                "Resumo da Fatura",
                "Saldo Atual"
        );

        InvoiceParserFactory factory = new InvoiceParserFactory("http://localhost:8000");
        InvoiceParserSelector.Selection selection = InvoiceParserSelector.selectBest(factory.getParsers(), text);

        assertEquals("BradescoInvoiceParser", selection.chosen().parser().getClass().getSimpleName());
        assertTrue(selection.chosen().applicable());
        assertTrue(selection.chosen().txCount() > 0);
    }

    @Test
    void selectsMercadoPagoForMercadoPagoText() {
        String text = String.join("\n",
                "Mercado Pago",
                "Essa é sua fatura de dezembro",
                "Total a pagar",
                "R$ 2.449,67",
                "Vence em",
                "23/12/2025",
                "",
                "Lançamentos",
                "17/12 UBER TRIP 18,40",
                "18/12 PAGAMENTO DA FATURA -100,00",
                "19/12 IFD*IFD*COMERCIO DE 120,90"
        );

        InvoiceParserFactory factory = new InvoiceParserFactory("http://localhost:8000");
        InvoiceParserSelector.Selection selection = InvoiceParserSelector.selectBest(factory.getParsers(), text);

        assertEquals("MercadoPagoInvoiceParser", selection.chosen().parser().getClass().getSimpleName());
        assertTrue(selection.chosen().applicable());
        assertTrue(selection.chosen().txCount() > 0);
    }
}
