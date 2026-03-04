package com.ella.backend.services.invoices.parsers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ella.backend.enums.TransactionType;

class C6ExtractorParserTest {

    @Test
    void extractorTotalDiverges_parserUsesInvoiceTotalFromText() {
        EllaExtractorClient client = mock(EllaExtractorClient.class);

        var txs = List.of(
                new EllaExtractorClient.C6InvoiceResponse.Tx(
                        "2025-10-27",
                        "AIRBNB * HMF99EFWK9",
                        369.48,
                        "5867",
                        null),
                new EllaExtractorClient.C6InvoiceResponse.Tx(
                        "2025-11-14",
                        "BAR PIMENTA CARIOCA",
                        92.40,
                        "5867",
                        null)
        );

        when(client.parseC6Invoice(any()))
                .thenReturn(new EllaExtractorClient.C6InvoiceResponse(
                        "C6",
                        "2025-12-20",
                        7012.90,
                        txs));

        C6ExtractorParser parser = new C6ExtractorParser(client);
        String text = String.join("\n",
                "Olá, Lenon! Sua fatura com vencimento em Dezembro chegou no valor de R$ 5.098,40.",
                "Valor da fatura: R$ 5.098,40",
                "Total a pagar R$ 5.098,40",
                "Parcelamento Total a pagar CET",
                "R$ 7.012,90 Entrada + 9x de R$ 701,29 152,52% a.a.");

        ParseResult actual = parser.parseWithPdf(new byte[] { 1, 2, 3 }, text);

        assertNotNull(actual);
        assertEquals(new BigDecimal("5098.40"), actual.getTotalAmount());
    }

    @Test
    void clientSucceeds_parserUsesExtractorTransactions() {
        EllaExtractorClient client = mock(EllaExtractorClient.class);

        var txs = List.of(
                new EllaExtractorClient.C6InvoiceResponse.Tx(
                        "2025-10-27",
                        "AIRBNB * HMF99EFWK9",
                        369.48,
                        "5867",
                        new EllaExtractorClient.C6InvoiceResponse.Installment(2, 3)),
                new EllaExtractorClient.C6InvoiceResponse.Tx(
                        "2025-11-21",
                        "Inclusao de Pagamento",
                        -5698.02,
                        "1234",
                        null)
        );

        when(client.parseC6Invoice(any()))
                .thenReturn(new EllaExtractorClient.C6InvoiceResponse(
                        "C6",
                        "2025-12-20",
                        5098.40,
                        txs));

        C6ExtractorParser parser = new C6ExtractorParser(client);
        ParseResult actual = parser.parseWithPdf(new byte[] { 1, 2, 3 }, "C6 BANK\nVencimento: 20/12/2025");

        assertNotNull(actual);
        assertEquals(LocalDate.of(2025, 12, 20), actual.getDueDate());
        assertNotNull(actual.getTransactions());
        // "Inclusao de Pagamento" deve ser ignorado
        assertEquals(1, actual.getTransactions().size());
        assertEquals("AIRBNB * HMF99EFWK9", actual.getTransactions().get(0).description);
        assertEquals(TransactionType.EXPENSE, actual.getTransactions().get(0).type);
        assertEquals(Integer.valueOf(2), actual.getTransactions().get(0).installmentNumber);
        assertEquals(Integer.valueOf(3), actual.getTransactions().get(0).installmentTotal);

        verify(client, times(1)).parseC6Invoice(any());
    }

    @Test
    void clientFails_parserFallsBackToTextParser() {
        EllaExtractorClient client = mock(EllaExtractorClient.class);
        doThrow(new RuntimeException("down")).when(client).parseC6Invoice(any());

        C6ExtractorParser parser = new C6ExtractorParser(client);

        String text = String.join("\n",
                "C6 BANK",
                "Vencimento: 20/12/2025",
                "Transações",
                "C6 Carbon Virtual Final 5867 - TITULAR",
                "14 nov BAR PIMENTA CARIOCA 92,40");

        ParseResult actual = parser.parseWithPdf(new byte[] { 9, 9, 9 }, text);

        assertNotNull(actual);
        assertEquals(LocalDate.of(2025, 12, 20), actual.getDueDate());
        assertNotNull(actual.getTransactions());
        assertEquals(1, actual.getTransactions().size());
        assertEquals("BAR PIMENTA CARIOCA", actual.getTransactions().get(0).description);

        verify(client, times(1)).parseC6Invoice(any());
    }
}
