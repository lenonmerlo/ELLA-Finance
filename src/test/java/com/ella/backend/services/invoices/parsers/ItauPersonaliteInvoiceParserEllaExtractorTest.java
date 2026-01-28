package com.ella.backend.services.invoices.parsers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

class ItauPersonaliteInvoiceParserEllaExtractorTest {

    @Test
    void clientFails_parserFallsBackToExistingTextParsing() {
        EllaExtractorClient client = mock(EllaExtractorClient.class);
                doThrow(new RuntimeException("down")).when(client).parseItauPersonnalite(any());

        ItauPersonaliteInvoiceParser parser = new ItauPersonaliteInvoiceParser(client);

        String text = String.join("\n",
                "Itau Personnalitê",
                "Resumo da fatura",
                "Vencimento: 21/11/2025",
                "",
                "Lançamentos: compras e saques",
                "17/11 UBER TRIP 18,40");

        List<TransactionData> expected = parser.extractTransactions(text);
        ParseResult actual = parser.parseWithPdf(new byte[] { 1, 2, 3 }, text);

        assertNotNull(actual);
        assertNotNull(actual.getTransactions());
        assertEquals(expected.size(), actual.getTransactions().size());
        assertEquals(expected.get(0).description, actual.getTransactions().get(0).description);

                verify(client, times(1)).parseItauPersonnalite(any());
    }

    @Test
    void clientSucceeds_parserUsesPythonTransactions() {
        EllaExtractorClient client = mock(EllaExtractorClient.class);

        var txs = List.of(
                new EllaExtractorClient.ItauPersonnaliteResponse.Tx(
                        "2025-11-17",
                        "UBER TRIP",
                        18.40,
                        "8578",
                        null),
                new EllaExtractorClient.ItauPersonnaliteResponse.Tx(
                        "17/11",
                        "ALLIANZ SEGUROS 09/10",
                        188.39,
                        "2673",
                        new EllaExtractorClient.ItauPersonnaliteResponse.Installment(9, 10))
        );

        when(client.parseItauPersonnalite(any()))
                .thenReturn(new EllaExtractorClient.ItauPersonnaliteResponse(
                        "itau_personnalite",
                        "2025-11-21",
                        3760.96,
                        txs));

        ItauPersonaliteInvoiceParser parser = new ItauPersonaliteInvoiceParser(client);

        String extractedText = String.join("\n",
                "Itau Personnalitê",
                "Resumo da fatura",
                "Vencimento: 21/11/2025");

        ParseResult actual = parser.parseWithPdf(new byte[] { 9, 9, 9 }, extractedText);

        assertNotNull(actual);
        assertEquals(2, actual.getTransactions().size());
        assertEquals("UBER TRIP", actual.getTransactions().get(0).description);
        assertEquals("Itau Personnalitê final 8578", actual.getTransactions().get(0).cardName);
        assertEquals(Integer.valueOf(9), actual.getTransactions().get(1).installmentNumber);
        assertEquals(Integer.valueOf(10), actual.getTransactions().get(1).installmentTotal);

                verify(client, times(1)).parseItauPersonnalite(any());
    }
}
