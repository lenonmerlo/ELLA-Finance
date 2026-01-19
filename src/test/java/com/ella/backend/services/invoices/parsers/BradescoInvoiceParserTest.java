package com.ella.backend.services.invoices.parsers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ella.backend.enums.TransactionType;

class BradescoInvoiceParserTest {

    @Test
    void detectsAndParsesDueDateAndTransactions() {
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

        BradescoInvoiceParser parser = new BradescoInvoiceParser();

        assertTrue(parser.isApplicable(text));

        LocalDate dueDate = parser.extractDueDate(text);
        assertEquals(LocalDate.of(2025, 12, 25), dueDate);

        List<TransactionData> txs = parser.extractTransactions(text);
        assertEquals(5, txs.size());

        TransactionData t1 = txs.get(0);
        assertEquals("CTCE FORTALEZA CE P/1", t1.description);
        assertEquals(0, t1.amount.compareTo(new BigDecimal("19813.33")));
        assertEquals(TransactionType.EXPENSE, t1.type);
        assertEquals("Hospedagem", t1.category);
        assertEquals(LocalDate.of(2025, 10, 27), t1.date);
        assertNotNull(t1.cardName);
        assertEquals("MARIANA OLIVEIRA DE CASTRO", t1.cardholderName);
        assertEquals(1, t1.installmentNumber);
        assertEquals(1, t1.installmentTotal);

        TransactionData t2 = txs.get(1);
        assertEquals("CUSTO TRANS. EXTERIOR-IOF", t2.description);
        assertEquals(0, t2.amount.compareTo(new BigDecimal("6.71")));
        assertEquals(TransactionType.EXPENSE, t2.type);
        assertEquals("Viagem", t2.category);
        assertEquals(LocalDate.of(2025, 11, 12), t2.date);

        TransactionData t3 = txs.get(2);
        assertEquals("UNIMED LITORAL", t3.description);
        assertEquals(0, t3.amount.compareTo(new BigDecimal("306.88")));
        assertEquals(TransactionType.EXPENSE, t3.type);
        assertEquals("Plano de Saúde", t3.category);
        assertEquals(LocalDate.of(2025, 3, 18), t3.date);

        TransactionData t4 = txs.get(3);
        assertEquals("BRADESCO AUTO", t4.description);
        assertEquals(0, t4.amount.compareTo(new BigDecimal("50.00")));
        assertEquals(TransactionType.EXPENSE, t4.type);
        assertEquals("Seguro", t4.category);
        assertEquals(LocalDate.of(2025, 12, 1), t4.date);

        TransactionData t5 = txs.get(4);
        assertEquals("PAYGOAL", t5.description);
        assertEquals(0, t5.amount.compareTo(new BigDecimal("10.00")));
        assertEquals(TransactionType.INCOME, t5.type);
        assertEquals("Reembolso", t5.category);
        assertEquals(LocalDate.of(2025, 12, 5), t5.date);
    }

    @Test
    void parsesTransactionsEvenWhenLaunchesMarkerIsMissingAndResumoAppearsEarly() {
        // Real-world OCR/PDFBox scenario: marker "LANÇAMENTOS" may be missing/garbled, while "Resumo" exists in header.
        String text = String.join("\n",
                "BRADESCO",
                "Resumo da Fatura",
                "Total da fatura: R$ 13.646,35",
                "Vencimento 25/11/2025",
                "",
                "Data  Histórico de Lançamentos                     Valor",
                "27/10 CTCE FORTALEZA CE P/1                    19.813,33",
                "12/11 CUSTO TRANS. EXTERIOR-IOF                   6,71",
                "01/12 BRADESCO AUTO                             50,00",
                "05/12 PAYGOAL                                  -10,00",
                "",
                "Algum rodapé"
        );

        BradescoInvoiceParser parser = new BradescoInvoiceParser();
        assertTrue(parser.isApplicable(text));
        assertEquals(LocalDate.of(2025, 11, 25), parser.extractDueDate(text));

        List<TransactionData> txs = parser.extractTransactions(text);
        assertEquals(4, txs.size());
        assertEquals("CTCE FORTALEZA CE P/1", txs.get(0).description);
        assertEquals("CUSTO TRANS. EXTERIOR-IOF", txs.get(1).description);
        assertEquals("BRADESCO AUTO", txs.get(2).description);
        assertEquals("PAYGOAL", txs.get(3).description);
        assertEquals(TransactionType.INCOME, txs.get(3).type);
    }

    @Test
    void ignoresContinuationLinesLikeCam() {
        String text = String.join("\n",
                "BRADESCO",
                "Vencimento: 25/12/2025",
                "LANÇAMENTOS",
                "Data  Histórico de Lançamentos                     Valor",
                "19/03 UNIMED LITORAL 08/10 BALNEARIO              306,88",
                "                                     CAM",
                "01/12 BRADESCO AUTO                                50,00",
                "Resumo da Fatura"
        );

        BradescoInvoiceParser parser = new BradescoInvoiceParser();
        List<TransactionData> txs = parser.extractTransactions(text);
        assertEquals(2, txs.size());

        assertTrue(txs.get(0).description.contains("UNIMED"));
        assertTrue(!txs.get(0).description.contains("CAM"));
        assertEquals(0, txs.get(0).amount.compareTo(new BigDecimal("306.88")));
    }

    @Test
    void parsesMultilineLaunchesWhereAmountIsOnNextLineAndIgnoresPaCamMarkers() {
        String text = String.join("\n",
                "BRADESCO",
                "Vencimento: 25/12/2025",
                "LANÇAMENTOS",
                "Data  Histórico de Lançamentos                     Valor",
                // Exemplo real: a linha do lançamento quebra e o valor aparece na linha seguinte
                "19/03 UNIMED LITORAL",
                "08/10 BALNEARIO 306,88",
                "CAM",
                "19/03 UNIMED LITORAL",
                "08/10 BALNEARIO 245,10",
                "CAM",
                "04/11 CLUBE LIVELO*Club",
                "01/12 SANTANA DE 42,71",
                "PA",
                "Resumo da Fatura"
        );

        BradescoInvoiceParser parser = new BradescoInvoiceParser();
        List<TransactionData> txs = parser.extractTransactions(text);

        assertEquals(3, txs.size());

        assertEquals("UNIMED LITORAL", txs.get(0).description);
        assertEquals(0, txs.get(0).amount.compareTo(new BigDecimal("306.88")));
        assertEquals(LocalDate.of(2025, 3, 19), txs.get(0).date);

        assertEquals("UNIMED LITORAL", txs.get(1).description);
        assertEquals(0, txs.get(1).amount.compareTo(new BigDecimal("245.10")));
        assertEquals(LocalDate.of(2025, 3, 19), txs.get(1).date);

        assertEquals("CLUBE LIVELO*Club", txs.get(2).description);
        assertEquals(0, txs.get(2).amount.compareTo(new BigDecimal("42.71")));
        assertEquals(LocalDate.of(2025, 11, 4), txs.get(2).date);
    }
}
