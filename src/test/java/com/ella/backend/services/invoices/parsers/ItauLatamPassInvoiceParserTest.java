package com.ella.backend.services.invoices.parsers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;


class ItauLatamPassInvoiceParserTest {

    @Test
    void isApplicable_detectsLatamPassLayout() {
        ItauLatamPassInvoiceParser parser = new ItauLatamPassInvoiceParser(mock(EllaExtractorLatamPassClient.class));

        String sampleTextLatamPass = String.join("\n",
                "BANCO ITAU",
                "Resumo da fatura",
                "Vencimento: 25/02/2026",
                "",
                "Pagamentos efetuados",
                "22/01 PAGAMENTO PIX -62,00",
                "",
                "Lançamentos: compras e saques",
                "05/02 LATAM AIR*0000 01/10 162,38",
                "",
                "Compras parceladas - próximas faturas",
                "05/02 LATAM AIR*0000 02/10 162,34"
        );

        assertTrue(parser.isApplicable(sampleTextLatamPass));
    }

    @Test
    void parseWithPdf_mapsExtractorResponseToParseResult() {
        EllaExtractorLatamPassClient client = mock(EllaExtractorLatamPassClient.class);

        var txs = List.of(
                new EllaExtractorLatamPassClient.ItauLatamPassResponse.Tx(
                        "2026-01-22",
                        "PAGAMENTO PIX",
                        -62.00,
                        null,
                        null,
                        null
                ),
                new EllaExtractorLatamPassClient.ItauLatamPassResponse.Tx(
                        "2026-01-18",
                        "Mensalidade Credito Rotativo Juros e encargos financeiros até o momento 0,00 %",
                        -62.00,
                        null,
                        null,
                        null
                ),
                new EllaExtractorLatamPassClient.ItauLatamPassResponse.Tx(
                        "2026-02-10",
                        "UBER TRIP",
                        18.40,
                        null,
                        null,
                        null
                ),
                new EllaExtractorLatamPassClient.ItauLatamPassResponse.Tx(
                        "2026-02-12",
                        "ALLIANZ SEGUROS 01/10",
                        188.39,
                        new EllaExtractorLatamPassClient.ItauLatamPassResponse.Installment(1, 10),
                        null,
                        null
                )
        );

        when(client.parseItauLatamPass(any()))
                .thenReturn(new EllaExtractorLatamPassClient.ItauLatamPassResponse(
                        "itau_latam_pass",
                        "2026-02-25",
                        1833.31,
                        txs
                ));

        ItauLatamPassInvoiceParser parser = new ItauLatamPassInvoiceParser(client);

        String extractedText = String.join("\n",
                "BANCO ITAU",
                "LATAM PASS",
                "Resumo da fatura",
                "Vencimento: 25/02/2026",
                "Lançamentos: compras e saques"
        );

        ParseResult actual = parser.parseWithPdf(new byte[] { 1, 2, 3, 4 }, extractedText);

        assertNotNull(actual);
        assertNotNull(actual.getDueDate());
        assertNotNull(actual.getTotalAmount());
        assertTrue(actual.getTotalAmount().doubleValue() > 0);
        assertNotNull(actual.getTransactions());
        assertTrue(actual.getTransactions().size() > 0);

                for (TransactionData tx : actual.getTransactions()) {
                        assertNotNull(tx);
                        assertNotNull(tx.date);
                        assertNotNull(tx.description);
                        assertTrue(!tx.description.isBlank());
                        assertNotNull(tx.amount);
                }

                // Payment lines are demonstrative and must be ignored.
                boolean hasPaymentPix = actual.getTransactions().stream()
                        .anyMatch(t -> t != null && t.description != null && t.description.toLowerCase().contains("pagamento pix"));
                assertEquals(false, hasPaymentPix);

                // Mensalidade is a charge (expense) even if extractor emits it as negative.
                TransactionData mensalidade = actual.getTransactions().stream()
                        .filter(t -> t != null && t.description != null && t.description.toLowerCase().contains("mensalidade"))
                        .findFirst()
                        .orElse(null);
                assertNotNull(mensalidade);
                assertEquals(com.ella.backend.enums.TransactionType.EXPENSE, mensalidade.type);
                assertEquals(62.00, mensalidade.amount.doubleValue(), 0.001);

                assertEquals("UBER TRIP", actual.getTransactions().stream()
                        .filter(t -> t != null && "UBER TRIP".equals(t.description))
                        .findFirst()
                        .orElseThrow().description);

                TransactionData allianz = actual.getTransactions().stream()
                        .filter(t -> t != null && t.description != null && t.description.startsWith("ALLIANZ"))
                        .findFirst()
                        .orElse(null);
                assertNotNull(allianz);
                assertEquals(Integer.valueOf(1), allianz.installmentNumber);
                assertEquals(Integer.valueOf(10), allianz.installmentTotal);

        verify(client, times(1)).parseItauLatamPass(any());
    }
}
