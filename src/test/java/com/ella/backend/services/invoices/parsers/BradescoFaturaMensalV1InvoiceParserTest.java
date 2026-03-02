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

class BradescoFaturaMensalV1InvoiceParserTest {

    @Test
    void isApplicable_detectsBradescoFaturaMensalLayout() {
        BradescoFaturaMensalV1InvoiceParser parser = new BradescoFaturaMensalV1InvoiceParser(
                mock(EllaExtractorBradescoFaturaMensalV1Client.class)
        );

        String sample = String.join("\n",
                "BRADESCO",
                "FATURA MENSAL",
                "Vencimento 25/02/2026",
                "Lançamentos",
                "Total da fatura em real R$ 15.681,84"
        );

        assertTrue(parser.isApplicable(sample));
    }

    @Test
    void parseWithPdf_mapsExtractorResponseToParseResult() {
        EllaExtractorBradescoFaturaMensalV1Client client = mock(EllaExtractorBradescoFaturaMensalV1Client.class);

        var txs = List.of(
                // Should be ignored (payment line)
                new EllaExtractorBradescoFaturaMensalV1Client.BradescoFaturaMensalV1Response.Tx(
                        "2026-02-11",
                        "PAGTO. POR DEB EM C/C",
                        16044.43,
                        null,
                        null,
                        null,
                        null
                ),
                // Should be ignored (limits table artifact)
                new EllaExtractorBradescoFaturaMensalV1Client.BradescoFaturaMensalV1Response.Tx(
                        "2026-02-11",
                        "DECORART COMERCIO Saque R$ 15.000,00 R$ 0,00 R$ 15.000,00",
                        15000.00,
                        null,
                        null,
                        null,
                        null
                ),
                new EllaExtractorBradescoFaturaMensalV1Client.BradescoFaturaMensalV1Response.Tx(
                        "2026-02-11",
                        "UBER TRIP",
                        18.40,
                        "SAO PAULO",
                        null,
                        null,
                        null
                ),
                new EllaExtractorBradescoFaturaMensalV1Client.BradescoFaturaMensalV1Response.Tx(
                        "2026-02-12",
                        "ALLIANZ SEGUROS",
                        188.39,
                        null,
                        1,
                        10,
                        null
                ),
                new EllaExtractorBradescoFaturaMensalV1Client.BradescoFaturaMensalV1Response.Tx(
                        "2026-02-13",
                        "MARY JOHN FORTALEZA Crédito Rotativo / Atraso / Parcelados",
                        210.00,
                        null,
                        null,
                        null,
                        null
                ),
                new EllaExtractorBradescoFaturaMensalV1Client.BradescoFaturaMensalV1Response.Tx(
                        "2026-02-15",
                        "ESTORNO MERCADORIA",
                        62.00,
                        null,
                        null,
                        null,
                        null
                )
        );

        when(client.parseBradescoFaturaMensalV1(any()))
                .thenReturn(new EllaExtractorBradescoFaturaMensalV1Client.BradescoFaturaMensalV1Response(
                        "bradesco_fatura_mensal_v1",
                        "2026-02-25",
                        15681.84,
                        txs,
                        null
                ));

        BradescoFaturaMensalV1InvoiceParser parser = new BradescoFaturaMensalV1InvoiceParser(client);

        ParseResult actual = parser.parseWithPdf(new byte[] { 1, 2, 3, 4 }, "BRADESCO FATURA MENSAL");

        assertNotNull(actual);
        assertNotNull(actual.getDueDate());
        assertEquals(2026, actual.getDueDate().getYear());
        assertNotNull(actual.getTotalAmount());
        assertTrue(actual.getTotalAmount().doubleValue() > 0);
        assertNotNull(actual.getTransactions());
        assertEquals(4, actual.getTransactions().size());

        TransactionData uber = actual.getTransactions().stream()
                .filter(t -> t != null && "UBER TRIP".equals(t.description))
                .findFirst()
                .orElseThrow();
        assertEquals(com.ella.backend.enums.TransactionType.EXPENSE, uber.type);
        assertEquals("Transporte", uber.category);

        TransactionData allianz = actual.getTransactions().stream()
                .filter(t -> t != null && t.description != null && t.description.startsWith("ALLIANZ"))
                .findFirst()
                .orElseThrow();
        assertEquals(Integer.valueOf(1), allianz.installmentNumber);
        assertEquals(Integer.valueOf(10), allianz.installmentTotal);
        assertEquals("Seguro", allianz.category);

        TransactionData estorno = actual.getTransactions().stream()
                .filter(t -> t != null && t.description != null && t.description.toLowerCase().contains("estorno"))
                .findFirst()
                .orElseThrow();
        assertEquals(com.ella.backend.enums.TransactionType.INCOME, estorno.type);
        assertEquals(62.00, estorno.amount.doubleValue(), 0.001);

        TransactionData mary = actual.getTransactions().stream()
                .filter(t -> t != null && t.description != null && t.description.toLowerCase().contains("mary john"))
                .findFirst()
                .orElseThrow();
        assertEquals(com.ella.backend.enums.TransactionType.EXPENSE, mary.type);
        assertEquals(210.00, mary.amount.doubleValue(), 0.001);

        verify(client, times(1)).parseBradescoFaturaMensalV1(any());
    }

    @Test
    void parseWithPdf_categorizesCommonBradescoMerchantsFromUserList() {
        EllaExtractorBradescoFaturaMensalV1Client client = mock(EllaExtractorBradescoFaturaMensalV1Client.class);

        var txs = List.of(
                new EllaExtractorBradescoFaturaMensalV1Client.BradescoFaturaMensalV1Response.Tx(
                        "2026-02-11",
                        "ENCARGOS DE ROTATIVO",
                        241.89,
                        null,
                        null,
                        null,
                        null
                ),
                new EllaExtractorBradescoFaturaMensalV1Client.BradescoFaturaMensalV1Response.Tx(
                        "2026-01-22",
                        "PRONACE FORTALEZA",
                        73.24,
                        null,
                        null,
                        null,
                        null
                ),
                new EllaExtractorBradescoFaturaMensalV1Client.BradescoFaturaMensalV1Response.Tx(
                        "2026-01-27",
                        "MERCADINHO SAO LUIZ FORTALEZA",
                        130.73,
                        null,
                        null,
                        null,
                        null
                ),
                new EllaExtractorBradescoFaturaMensalV1Client.BradescoFaturaMensalV1Response.Tx(
                        "2026-01-30",
                        "PAGUE MENOS@ 08 5 CE",
                        75.68,
                        null,
                        null,
                        null,
                        null
                ),
                new EllaExtractorBradescoFaturaMensalV1Client.BradescoFaturaMensalV1Response.Tx(
                        "2026-01-15",
                        "CLUBE LIVELO*Club",
                        369.90,
                        null,
                        null,
                        null,
                        null
                ),
                new EllaExtractorBradescoFaturaMensalV1Client.BradescoFaturaMensalV1Response.Tx(
                        "2026-01-09",
                        "ONE PARK CEARA FORTALEZA",
                        15.00,
                        null,
                        null,
                        null,
                        null
                ),
                new EllaExtractorBradescoFaturaMensalV1Client.BradescoFaturaMensalV1Response.Tx(
                        "2026-01-22",
                        "PAO DE ACUCAR-1220 FORTALEZA em fatura. Válido por cada operação de parcelamento ou",
                        71.34,
                        null,
                        null,
                        null,
                        null
                )
        );

        when(client.parseBradescoFaturaMensalV1(any()))
                .thenReturn(new EllaExtractorBradescoFaturaMensalV1Client.BradescoFaturaMensalV1Response(
                        "bradesco_fatura_mensal_v1",
                        "2026-02-25",
                        15681.84,
                        txs,
                        null
                ));

        BradescoFaturaMensalV1InvoiceParser parser = new BradescoFaturaMensalV1InvoiceParser(client);
        ParseResult actual = parser.parseWithPdf(new byte[] { 1, 2, 3 }, "BRADESCO FATURA MENSAL");

        assertEquals("Taxas e Juros", categoryOf(actual, "ENCARGOS DE ROTATIVO"));
        assertEquals("Saúde", categoryOf(actual, "PRONACE FORTALEZA"));
        assertEquals("Alimentação", categoryOf(actual, "MERCADINHO SAO LUIZ FORTALEZA"));
        assertEquals("Saúde", categoryOf(actual, "PAGUE MENOS@ 08 5 CE"));
        assertEquals("Serviços", categoryOf(actual, "CLUBE LIVELO*Club"));
        assertEquals("Lazer", categoryOf(actual, "ONE PARK CEARA FORTALEZA"));
                assertEquals("Alimentação", categoryOf(actual, "PAO DE ACUCAR-1220 FORTALEZA em fatura. Válido por cada operação de parcelamento ou"));
    }

    private String categoryOf(ParseResult result, String description) {
        return result.getTransactions().stream()
                .filter(t -> t != null && description.equals(t.description))
                .map(t -> t.category)
                .findFirst()
                .orElseThrow();
    }
}
