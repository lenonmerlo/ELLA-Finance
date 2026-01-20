package com.ella.backend.services.invoices.parsers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ella.backend.enums.TransactionType;

class BancoDoBrasilInvoiceParserTest {

    @Test
    void detectsAndParsesDueDateAndTransactionsIncludingInternationalContinuation() {
        String text = String.join("\n",
                "BANCO DO BRASIL",
                "OUROCARD",
                "Total da fatura: R$ 1.234,56",
                "Vencimento 20/09/2025",
                "",
                "Descrição  País  Valor",
                "Lazer",
                "20/08 PGTO. COBRANCA 2958 0000000200 200 BR R$ -84,00",
                "21/08 WWW.STATUEOFLIBERTYTICK866-5689827 TX R$ 79,68",
                "      *** 14,00 DOLAR AMERICANO",
                "      Cotação do Dólar de 21/08: R$ 5,6915",
            // Simula extração onde o PDF cola linhas de detalhe na mesma linha da transação
            "21/08 911 MUSEUM WEB 646-757-5567 *** 72,00 DOLAR AMERICANO Cotação do Dólar de 21/08: R$ 5,6915 NY R$ 409,79",
                "Serviços",
                "22/08 ANUIDADE DIFERENCIADA BR R$ 10,00",
                "",
                "Resumo da Fatura"
        );

        BancoDoBrasilInvoiceParser parser = new BancoDoBrasilInvoiceParser();

        assertTrue(parser.isApplicable(text));

        LocalDate dueDate = parser.extractDueDate(text);
        assertEquals(LocalDate.of(2025, 9, 20), dueDate);

        List<TransactionData> txs = parser.extractTransactions(text);
        // "PGTO. COBRANCA ..." deve ser ignorado (pagamento de fatura anterior/adiantamento)
        assertEquals(3, txs.size());

        TransactionData t1 = txs.get(0);
        assertEquals("WWW.STATUEOFLIBERTYTICK866-5689827", t1.description);
        assertEquals(0, t1.amount.compareTo(new BigDecimal("79.68")));
        assertEquals(TransactionType.EXPENSE, t1.type);
        assertEquals(LocalDate.of(2025, 8, 21), t1.date);

        TransactionData t2 = txs.get(1);
        assertEquals("911 MUSEUM WEB 646-757-5567", t2.description);
        assertEquals(0, t2.amount.compareTo(new BigDecimal("409.79")));
        assertEquals(TransactionType.EXPENSE, t2.type);
        assertEquals(LocalDate.of(2025, 8, 21), t2.date);

        TransactionData t3 = txs.get(2);
        assertEquals("ANUIDADE DIFERENCIADA", t3.description);
        assertEquals(0, t3.amount.compareTo(new BigDecimal("10.00")));
        assertEquals(TransactionType.EXPENSE, t3.type);
        assertEquals("Taxas e Juros", t3.category);
        assertEquals(LocalDate.of(2025, 8, 22), t3.date);
    }
}
