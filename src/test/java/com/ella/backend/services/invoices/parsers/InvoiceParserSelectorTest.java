package com.ella.backend.services.invoices.parsers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class InvoiceParserSelectorTest {

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

        InvoiceParserFactory factory = new InvoiceParserFactory();
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

        InvoiceParserFactory factory = new InvoiceParserFactory();
        InvoiceParserSelector.Selection selection = InvoiceParserSelector.selectBest(factory.getParsers(), text);

        assertEquals("MercadoPagoInvoiceParser", selection.chosen().parser().getClass().getSimpleName());
        assertTrue(selection.chosen().applicable());
        assertTrue(selection.chosen().txCount() > 0);
    }
}
