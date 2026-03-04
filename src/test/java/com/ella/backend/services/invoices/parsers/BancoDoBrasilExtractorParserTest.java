package com.ella.backend.services.invoices.parsers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ella.backend.enums.TransactionType;

class BancoDoBrasilExtractorParserTest {

    @Test
    void clientSucceeds_parserUsesExtractorTransactions() {
        EllaExtractorClient client = mock(EllaExtractorClient.class);

        var txs = List.of(
                new EllaExtractorClient.BancoDoBrasilResponse.Tx(
                        "2025-08-21",
                        "911 MUSEUM WEB 646-757-5567",
                        409.79,
                        "9194",
                        null),
                new EllaExtractorClient.BancoDoBrasilResponse.Tx(
                        "2025-08-20",
                        "PGTO. COBRANCA 2958",
                        -84.00,
                        "9194",
                        null)
        );

        when(client.parseBancoDoBrasil(any()))
                .thenReturn(new EllaExtractorClient.BancoDoBrasilResponse(
                        "BANCO_DO_BRASIL",
                        "2025-09-25",
                        14118.91,
                        txs));

        BancoDoBrasilExtractorParser parser = new BancoDoBrasilExtractorParser(client);
        ParseResult actual = parser.parseWithPdf(new byte[] { 1, 2, 3 }, "BANCO DO BRASIL\nVencimento 25/09/2025");

        assertNotNull(actual);
        assertEquals(LocalDate.of(2025, 9, 25), actual.getDueDate());
        assertNotNull(actual.getTransactions());
        // payment of previous invoice must be filtered out
        assertEquals(1, actual.getTransactions().size());
        assertEquals("911 MUSEUM WEB 646-757-5567", actual.getTransactions().get(0).description);
        assertEquals(TransactionType.EXPENSE, actual.getTransactions().get(0).type);
        assertTrue(actual.getTransactions().get(0).cardName.contains("9194"));

        verify(client, times(1)).parseBancoDoBrasil(any());
    }

    @Test
    void clientFails_parserFallsBackToTextParser() {
        EllaExtractorClient client = mock(EllaExtractorClient.class);
        doThrow(new RuntimeException("down")).when(client).parseBancoDoBrasil(any());

        BancoDoBrasilExtractorParser parser = new BancoDoBrasilExtractorParser(client);

        String text = String.join("\n",
                "BANCO DO BRASIL",
                "OUROCARD",
                "Resumo da fatura",
                "Vencimento 25/09/2025",
                "Data Descrição País Valor",
                "21/08 911 MUSEUM WEB NY R$ 409,79",
                "Total da Fatura R$ 14.118,91");

        ParseResult actual = parser.parseWithPdf(new byte[] { 9, 9, 9 }, text);

        assertNotNull(actual);
        assertEquals(LocalDate.of(2025, 9, 25), actual.getDueDate());
        assertNotNull(actual.getTransactions());
        assertEquals(1, actual.getTransactions().size());

        verify(client, times(1)).parseBancoDoBrasil(any());
    }
}
