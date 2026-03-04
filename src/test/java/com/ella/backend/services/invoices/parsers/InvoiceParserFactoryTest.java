package com.ella.backend.services.invoices.parsers;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class InvoiceParserFactoryTest {

    @Test
    void selectsNubankParserForNubankInvoice() {
        String nubankText = String.join("\n",
                "Esta é a sua fatura de dezembro, no valor de R$ 1.107,60",
                "Nubank",
                "Data de vencimento: 12 DEZ 2025",
                "Transações",
                "Compras parceladas - próximas faturas");

        InvoiceParserFactory factory = new InvoiceParserFactory("http://localhost:8000");
        Optional<InvoiceParserStrategy> parser = factory.getParser(nubankText);

        assertTrue(parser.isPresent());
        assertTrue(parser.get() instanceof NubankInvoiceParser);
    }

    @Test
    void selectsItauRegularParserForRegularItauInvoice() {
        String itauRegularText = String.join("\n",
                "Banco Itaú",
                "Resumo da fatura",
                "Total desta fatura",
                "Pagamento mínimo",
                "Vencimento: 21/11/2025",
                "Pagamentos efetuados",
                "Lançamentos: compras e saques",
                "17/11 UBER TRIP 18,40");

        InvoiceParserFactory factory = new InvoiceParserFactory("http://localhost:8000");
        Optional<InvoiceParserStrategy> parser = factory.getParser(itauRegularText);

        assertTrue(parser.isPresent());
        assertTrue(parser.get() instanceof ItauInvoiceParser);
    }

    @Test
    void selectsItauPersonaliteParserForPersonaliteInvoice() {
        String personaliteText = String.join("\n",
                "ITAU PERSONNALITE",
                "MASTERCARD",
                "Com vencimento em: 01/12/2025",
                "Lançamentos no cartão (final 8578)",
                "Lançamentos: compras e saques",
                "17/11 ALLIANZ SEGU*09 de 10 188,39");

        InvoiceParserFactory factory = new InvoiceParserFactory("http://localhost:8000");
        Optional<InvoiceParserStrategy> parser = factory.getParser(personaliteText);

        assertTrue(parser.isPresent());
        assertTrue(parser.get() instanceof ItauPersonaliteInvoiceParser);
    }

    @Test
    void selectsItauPersonaliteParserForGarbledPersonaliteInvoiceWithoutItauWord() {
        // Reproduz o caso real do log: PDFBox pode extrair "Lanamentos" (sem 'c') e não trazer "ITAU" no header.
        String garbledPersonalite = String.join("\n",
                "Resumo da fatura em R$",
                "Com vencimento em: 01/12/2025",
                "Lanamentos no carto (final 8578)",
                "Lanamentos: compras e saques",
                "19/05 COS SERVICOSMEDIC07/10 500,00",
                "Total dos lanamentos atuais 3.760,96");

        InvoiceParserFactory factory = new InvoiceParserFactory("http://localhost:8000");
        Optional<InvoiceParserStrategy> parser = factory.getParser(garbledPersonalite);

        assertTrue(parser.isPresent());
        assertTrue(parser.get() instanceof ItauPersonaliteInvoiceParser);
    }

    @Test
    void exposesOrderedParserList() {
        InvoiceParserFactory factory = new InvoiceParserFactory("http://localhost:8000");
        assertNotNull(factory.getParsers());
        assertTrue(factory.getParsers().size() > 0);
        // Hard safety assertion: Personalité should be evaluated before Nubank
        int personaliteIndex = -1;
        int nubankIndex = -1;
        for (int i = 0; i < factory.getParsers().size(); i++) {
            InvoiceParserStrategy p = factory.getParsers().get(i);
            if (p instanceof ItauPersonaliteInvoiceParser) personaliteIndex = i;
            if (p instanceof NubankInvoiceParser) nubankIndex = i;
        }
        assertTrue(personaliteIndex >= 0);
        assertTrue(nubankIndex >= 0);
        assertTrue(personaliteIndex < nubankIndex);
    }

    @Test
    void selectorPrefersItauPersonaliteOverSantanderForGarbledPersonaliteExcerpt() {
        String excerpt = String.join("\n",
                "R$ 3.760,96 01/12/2025 R$39.360,00",
                "Resumo da fatura em R$",
                "Lanamentos atuais 3.760,96",
                "19/05 COS SERVICOSMEDIC07/10 500,00",
                "Lanamentos: compras e saques",
                "Lanamentos no carto (final 8578) 2.872,08",
                "Total dos lanamentos atuais 3.760,96",
                // PDFBox pode quebrar "próximas" no meio.
                "Compras parceladas - pr",
                "ximas faturas",
                "19/05 COS SERVICOSMEDIC08/10 500,00");

        InvoiceParserFactory factory = new InvoiceParserFactory("http://localhost:8000");
        InvoiceParserSelector.Selection selection = InvoiceParserSelector.selectBest(factory.getParsers(), excerpt);

        assertNotNull(selection);
        assertNotNull(selection.chosen());
        assertTrue(selection.chosen().parser() instanceof ItauPersonaliteInvoiceParser);
    }

    @Test
    void selectsSantanderExtractorParserForSantanderLayout() {
        String santanderText = String.join("\n",
                "SANTANDER",
                "Total a Pagar R$ 1.381,94",
                "Vencimento 25/02/2026",
                "MARISA A O CASTRO - 5228 XXXX XXXX 5916",
                "Despesas",
                "22/01 PAPEL JORNAL PAPELARIA 79,80");

        InvoiceParserFactory factory = new InvoiceParserFactory("http://localhost:8000");
        Optional<InvoiceParserStrategy> parser = factory.getParser(santanderText);

        assertTrue(parser.isPresent());
        assertTrue(parser.get() instanceof SantanderExtractorParser);
    }

    @Test
    void selectsBancoDoBrasilExtractorParserForBbLayout() {
        String bbText = String.join("\n",
                "BANCO DO BRASIL",
                "OUROCARD VISA INFINITE Final 9194",
                "Resumo da fatura",
                "Total da fatura R$ 14.118,91",
                "Vencimento 25/09/2025",
                "Data Descrição País Valor",
                "21/08 911 MUSEUM WEB NY R$ 409,79");

        InvoiceParserFactory factory = new InvoiceParserFactory("http://localhost:8000");
        Optional<InvoiceParserStrategy> parser = factory.getParser(bbText);

        assertTrue(parser.isPresent());
        assertTrue(parser.get() instanceof BancoDoBrasilExtractorParser);
    }

    @Test
    void selectsC6ExtractorParserForC6InvoiceLayout() {
        String c6Text = String.join("\n",
                "C6 BANK",
                "Olá! Sua fatura com vencimento em Dezembro chegou no valor de R$ 5.098,40",
                "Vencimento: 20/12/2025",
                "Transações do cartão principal",
                "C6 Carbon Virtual Final 5867 - TITULAR",
                "27 out AIRBNB * HMF99EFWK9 - Parcela 2/3 369,48");

        InvoiceParserFactory factory = new InvoiceParserFactory("http://localhost:8000");
        Optional<InvoiceParserStrategy> parser = factory.getParser(c6Text);

        assertTrue(parser.isPresent());
        assertTrue(parser.get() instanceof C6ExtractorParser);
    }
}
