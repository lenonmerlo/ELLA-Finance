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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ella.backend.enums.TransactionType;

class SantanderExtractorParserTest {

        private static final String FALLBACK_RICH_TEXT = String.join("\n",
                        "SANTANDER",
                        "Total a Pagar R$ 44.815,95 Vencimento 20/12/2025",
                        "ATILLA FERREGUETTI - 4258 XXXX XXXX 8854",
                        "DESPESAS",
                        "17/11 RESTAURANTE VEGETARIA 43,75",
                        "18/11 IFD*IFD*COMERCIO DE 120,90",
                        "19/11 MERCADO TESTE 50,00",
                        "20/11 UBER TRIP 19,92",
                        "21/11 PAO DE ACUCAR-1296 344,78",
                        "22/11 DROGASIL1231 42,49",
                        "23/11 TOKIO MARINE*AUTO 221,06",
                        "24/11 AIRBNB * TESTE 496,57",
                        "25/11 WELLHUB GYMPASS BR GYMPAS 399,90",
                        "26/11 NETFLIX COM 59,90",
                        "27/11 POSTO PIO XII COM DE 100,00",
                        "28/11 CEA VSH 650 ECPC 90,00",
                        "29/11 ATACADO SAO PAULO LTDA 87,09");

    @Test
    void clientSucceeds_parserUsesExtractorTransactions() {
        EllaExtractorClient client = mock(EllaExtractorClient.class);

        var txs = List.of(
                new EllaExtractorClient.SantanderResponse.Tx(
                        "2026-02-18",
                        "ANUIDADE DIFERENCIADA",
                        166.66,
                        "6605",
                        null),
                new EllaExtractorClient.SantanderResponse.Tx(
                        "2026-02-16",
                        "IFD*IFOOD CLUB",
                        7.95,
                        "5916",
                        new EllaExtractorClient.SantanderResponse.Installment(2, 3))
        );

        when(client.parseSantander(any()))
                .thenReturn(new EllaExtractorClient.SantanderResponse(
                        "SANTANDER",
                        "2026-02-25",
                        174.61,
                        txs));

        SantanderExtractorParser parser = new SantanderExtractorParser(client);
        ParseResult actual = parser.parseWithPdf(new byte[] { 1, 2, 3 }, "SANTANDER\nVencimento 25/02/2026");

        assertNotNull(actual);
        assertEquals(LocalDate.of(2026, 2, 25), actual.getDueDate());
        assertEquals(0, new BigDecimal("174.61").compareTo(actual.getTotalAmount()));
        assertEquals(2, actual.getTransactions().size());

        TransactionData tx1 = actual.getTransactions().get(0);
        assertEquals("ANUIDADE DIFERENCIADA", tx1.description);
        assertEquals(TransactionType.EXPENSE, tx1.type);
        assertEquals("Santander final 6605", tx1.cardName);

        TransactionData tx2 = actual.getTransactions().get(1);
        assertEquals("IFD*IFOOD CLUB", tx2.description);
        assertEquals(Integer.valueOf(2), tx2.installmentNumber);
        assertEquals(Integer.valueOf(3), tx2.installmentTotal);
        assertEquals("Santander final 5916", tx2.cardName);

        verify(client, times(1)).parseSantander(any());
    }

    @Test
    void clientFails_parserFallsBackToTextParser() {
        EllaExtractorClient client = mock(EllaExtractorClient.class);
        doThrow(new RuntimeException("down")).when(client).parseSantander(any());

        SantanderExtractorParser parser = new SantanderExtractorParser(client);

        String text = String.join("\n",
                "SANTANDER",
                "Total a Pagar R$ 166,66 Vencimento 25/02/2026",
                "MARIANA O DE CASTRO - 5228 XXXX XXXX 6605",
                "DESPESAS",
                "18/02 ANUIDADE DIFERENCIADA 166,66");

        ParseResult actual = parser.parseWithPdf(new byte[] { 9, 9, 9 }, text);

        assertNotNull(actual);
        assertEquals(LocalDate.of(2026, 2, 25), actual.getDueDate());
        assertNotNull(actual.getTransactions());
        assertEquals(1, actual.getTransactions().size());
        assertEquals("ANUIDADE DIFERENCIADA", actual.getTransactions().get(0).description);
        assertTrue(actual.getTransactions().get(0).cardName.contains("6605"));

        verify(client, times(1)).parseSantander(any());
    }

        @Test
        void missingDueDateFromExtractor_fallsBackToTextParser() {
                EllaExtractorClient client = mock(EllaExtractorClient.class);

                var txs = List.of(
                                new EllaExtractorClient.SantanderResponse.Tx(
                                                "2026-11-13",
                                                "RESTAURANTE VEGETARIA 100,00 2 29/11 KL INTERNET 0213158458700",
                                                3371.82,
                                                "8854",
                                                new EllaExtractorClient.SantanderResponse.Installment(1, 4))
                );

                when(client.parseSantander(any()))
                                .thenReturn(new EllaExtractorClient.SantanderResponse(
                                                "SANTANDER",
                                                null,
                                                44815.95,
                                                txs));

                SantanderExtractorParser parser = new SantanderExtractorParser(client);

                String text = String.join("\n",
                                "SANTANDER",
                                "Total a Pagar R$ 166,66 Vencimento 25/02/2026",
                                "MARIANA O DE CASTRO - 5228 XXXX XXXX 6605",
                                "DESPESAS",
                                "18/02 ANUIDADE DIFERENCIADA 166,66");

                ParseResult actual = parser.parseWithPdf(new byte[] { 9, 9, 9 }, text);

                assertNotNull(actual);
                assertEquals(LocalDate.of(2026, 2, 25), actual.getDueDate());
                assertNotNull(actual.getTransactions());
                assertEquals(1, actual.getTransactions().size());
                assertEquals("ANUIDADE DIFERENCIADA", actual.getTransactions().get(0).description);

                verify(client, times(1)).parseSantander(any());
        }

    @Test
    void extractorUnderReads_prefersTextFallback() {
        EllaExtractorClient client = mock(EllaExtractorClient.class);

        var txs = List.of(
                new EllaExtractorClient.SantanderResponse.Tx(
                        "2025-11-17",
                        "RESTAURANTE VEGETARIA",
                        43.75,
                        "8854",
                        null)
        );

        when(client.parseSantander(any()))
                .thenReturn(new EllaExtractorClient.SantanderResponse(
                        "SANTANDER",
                        "2025-12-20",
                        44815.95,
                        txs));

        SantanderExtractorParser parser = new SantanderExtractorParser(client);
        ParseResult actual = parser.parseWithPdf(new byte[] { 7, 7, 7 }, FALLBACK_RICH_TEXT);

        assertNotNull(actual);
        assertEquals(LocalDate.of(2025, 12, 20), actual.getDueDate());
        assertNotNull(actual.getTransactions());
        assertTrue(actual.getTransactions().size() >= 10);
        assertEquals("RESTAURANTE VEGETARIA", actual.getTransactions().get(0).description);

        verify(client, times(1)).parseSantander(any());
    }

    @Test
    void ignoresPreviousInvoicePaymentButKeepsRefundCredits() {
        EllaExtractorClient client = mock(EllaExtractorClient.class);

        var txs = List.of(
                new EllaExtractorClient.SantanderResponse.Tx(
                        "2025-11-21",
                        "DEB AUTOM DE FATURA EM C/",
                        -7131.52,
                        "8854",
                        null),
                new EllaExtractorClient.SantanderResponse.Tx(
                        "2025-11-17",
                        "EST ANUIDADE DIFERENCIADA T",
                        -43.75,
                        "8854",
                        null),
                new EllaExtractorClient.SantanderResponse.Tx(
                        "2025-11-30",
                        "AIRBNB * HM39HDCYZW",
                        -7464.87,
                        "8305",
                        null),
                new EllaExtractorClient.SantanderResponse.Tx(
                        "2025-10-11",
                        "PG *CALVIN KLEIN",
                        -2824.20,
                        "8305",
                        null)
        );

        when(client.parseSantander(any()))
                .thenReturn(new EllaExtractorClient.SantanderResponse(
                        "SANTANDER",
                        "2025-12-20",
                        44815.95,
                        txs));

        SantanderExtractorParser parser = new SantanderExtractorParser(client);
        ParseResult actual = parser.parseWithPdf(new byte[] { 4, 4, 4 }, "SANTANDER\nVencimento 20/12/2025");

        assertNotNull(actual);
        assertNotNull(actual.getTransactions());
        assertEquals(3, actual.getTransactions().size());
        assertTrue(actual.getTransactions().stream().noneMatch(t -> "DEB AUTOM DE FATURA EM C/".equals(t.description)));
        assertTrue(actual.getTransactions().stream().anyMatch(t -> "EST ANUIDADE DIFERENCIADA T".equals(t.description)));
        assertTrue(actual.getTransactions().stream().anyMatch(t -> "AIRBNB * HM39HDCYZW".equals(t.description)));
        assertTrue(actual.getTransactions().stream().anyMatch(t -> "PG *CALVIN KLEIN".equals(t.description)));
    }
}
