package com.ella.backend.services.invoices.parsers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ItauPersonaliteInvoiceParserApplicabilityTest {

    @Test
    void acceptsWhenItauIsGarbledAsItaUnibanco() {
        ItauPersonaliteInvoiceParser parser = new ItauPersonaliteInvoiceParser();

        String text = String.join("\n",
                "Ita Unibanco",
                "Resumo da fatura em R$",
                "Lanamentos atuais 3.760,96",
                "Lanamentos no carto (final 8578)",
                "Lanamentos: compras e saques",
                "ITAU PERSONNALITÉ",
                "17/11 UBER TRIP 18,40"
        );

        assertTrue(parser.isApplicable(text));
    }

    @Test
    void acceptsWhenItauIsGarbledAsItaCares() {
        ItauPersonaliteInvoiceParser parser = new ItauPersonaliteInvoiceParser();

        String text = String.join("\n",
                "Ita Cares",
                "Resumo da fatura em R$",
                "Lanamentos atuais 3.760,96",
                "Lanamentos no carto (final 8578)",
                "Lanamentos: compras e saques",
                "PERSONNALITE",
                "17/11 UBER TRIP 18,40"
        );

        assertTrue(parser.isApplicable(text));
    }

    @Test
    void acceptsWhenPersonaliteIsBrokenIntoSingleLetters() {
        ItauPersonaliteInvoiceParser parser = new ItauPersonaliteInvoiceParser();

        String text = String.join("\n",
                "Ita Unibanco",
                "Resumo da fatura em R$",
                "Lanamentos atuais 3.760,96",
                "Lanamentos no carto (final 8578)",
                "Lanamentos: compras e saques",
                "p e r s o n a l i t e",
                "17/11 UBER TRIP 18,40"
        );

        assertTrue(parser.isApplicable(text));
    }

    @Test
    void acceptsWhenPersonaliteTokenIsMissingButPremiumMarkersExist() {
        ItauPersonaliteInvoiceParser parser = new ItauPersonaliteInvoiceParser();

        // Real-world PDFBox extraction can drop the "Personnalité" word entirely (logo/arte).
        // In this case we still want the Personalité parser to win over other parsers.
        String text = String.join("\n",
                "Resumo da fatura em R$",
                "Ita Cares",
                "5234.XXXX.XXXX.8578 MASTERCARD BLACK",
                "Lanamentos atuais 3.760,96",
                "Lanamentos no carto (final 8578)",
                "Lanamentos: compras e saques",
                "Vencimento: 01/12/2025",
                "17/11 UBER TRIP 18,40"
        );

        assertTrue(parser.isApplicable(text));
    }

    @Test
    void acceptsWhenKeyMarkersUseNbspSpacingFromPdfExtraction() {
        ItauPersonaliteInvoiceParser parser = new ItauPersonaliteInvoiceParser();

        // PDFBox can emit NBSP (\u00A0) instead of normal spaces.
        String nbsp = "\u00A0";
        String text = String.join("\n",
                "Resumo da fatura em R$",
                ("Ita" + nbsp + nbsp + "Cares"),
                ("5234.XXXX.XXXX.8578" + nbsp + "MASTERCARD" + nbsp + "BLACK"),
                ("Lanamentos" + nbsp + "atuais 3.760,96"),
                ("Lanamentos" + nbsp + "no carto (final 8578)"),
                ("Lanamentos:" + nbsp + "compras e saques"),
                "Vencimento: 01/12/2025",
                "17/11 UBER TRIP 18,40"
        );

        assertTrue(parser.isApplicable(text));
    }

    @Test
    void rejectsRegularItauInvoiceWithoutAnyPersonaliteToken() {
        ItauPersonaliteInvoiceParser parser = new ItauPersonaliteInvoiceParser();

        String text = String.join("\n",
                "Ita Unibanco",
                // Deliberately weak/partial: avoids the strong Itaú invoice markers used by the Personalité gate.
                "Vencimento: 21/11/2025",
                "Lançamentos: compras e saques",
                "17/11 UBER TRIP 18,40"
        );

        assertFalse(parser.isApplicable(text));
    }

    @Test
    void rejectsRegularItauInvoiceThatContainsOnlyPremiumCardMarker() {
        ItauPersonaliteInvoiceParser parser = new ItauPersonaliteInvoiceParser();

        // Regression: regular Itaú invoices can contain "Infinite"/"Visa Infinite" but are not Personalité.
        // Without explicit Personalité token and without "final 1234" / "Ita Cares" markers,
        // the Personalité parser must not steal the invoice.
        String text = String.join("\n",
                "ITAU UNIBANCO S.A.",
                "Resumo da fatura em R$",
                "Lançamentos atuais 2.005,92",
                "Pagamento mínimo: R$ 200,59",
                "Vencimento: 22/12/2025",
                "Infinite",
                "Lançamentos: compras e saques",
                "15/10 CLINICA SCHUNK 02/05 720,00"
        );

        assertFalse(parser.isApplicable(text));
    }
}
