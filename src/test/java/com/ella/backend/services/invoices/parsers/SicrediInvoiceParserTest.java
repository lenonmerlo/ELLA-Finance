package com.ella.backend.services.invoices.parsers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ella.backend.enums.TransactionType;

class SicrediInvoiceParserTest {

    @Test
    void detectsAndParsesDueDateAndTabularTransactions() {
        String text = String.join("\n",
                "SICREDI",
                "Vencimento 25/11/2025",
                "Total fatura de novembro R$ 12.068,55",
                "",
                "Cartão Mariana O Castro (final 2127)",
                "Data e hora          Cidade          Compra          Descrição          Parcela          Valor em reais",
                "11/nov 06:13         Fortaleza       Presencial      Uber Trip          07/12           R$ 4,90",
                "12/nov 10:00         Fortaleza       Online          Anuidade Diferenc  07/12           R$ 12,00",
                "20/out 09:00         Miami           Presencial      Apple Com/bill     01/03           $14.99   5,10   R$ 76,45",
                "15/nov 12:00         Fortaleza       Online          Pagamento 444400130                -R$ 934,83",
                "",
                "Cartão Virtual (final 9999)",
                "21/nov 18:30         Fortaleza       Presencial      Assai Atacadista Lj27              R$ 120,00"
        );

        SicrediInvoiceParser parser = new SicrediInvoiceParser();
        assertTrue(parser.isApplicable(text));

        LocalDate dueDate = parser.extractDueDate(text);
        assertEquals(LocalDate.of(2025, 11, 25), dueDate);

        List<TransactionData> txs = parser.extractTransactions(text);
        assertNotNull(txs);
        assertEquals(5, txs.size());

        TransactionData t1 = txs.get(0);
        assertEquals("Uber Trip", t1.description);
        assertEquals(TransactionType.EXPENSE, t1.type);
        assertEquals("Transporte", t1.category);
        assertEquals(0, t1.amount.compareTo(new BigDecimal("4.90")));
        assertEquals(LocalDate.of(2025, 11, 11), t1.date);
        assertNotNull(t1.cardName);
        assertEquals(7, t1.installmentNumber);
        assertEquals(12, t1.installmentTotal);

        TransactionData t2 = txs.get(1);
        assertEquals("Anuidade Diferenc", t2.description);
        assertEquals(TransactionType.EXPENSE, t2.type);
        assertEquals("Taxas e Juros", t2.category);
        assertEquals(0, t2.amount.compareTo(new BigDecimal("12.00")));
        assertEquals(LocalDate.of(2025, 11, 12), t2.date);
        assertEquals(7, t2.installmentNumber);
        assertEquals(12, t2.installmentTotal);

        TransactionData t3 = txs.get(2);
        assertEquals("Apple Com/bill", t3.description);
        assertEquals(TransactionType.EXPENSE, t3.type);
        assertEquals("Assinaturas", t3.category);
        assertEquals(0, t3.amount.compareTo(new BigDecimal("76.45")));
        // mês 10 (out) com vencimento em nov: mesmo ano
        assertEquals(LocalDate.of(2025, 10, 20), t3.date);
        assertEquals(1, t3.installmentNumber);
        assertEquals(3, t3.installmentTotal);

        TransactionData t4 = txs.get(3);
        assertEquals("Pagamento 444400130", t4.description);
        assertEquals(TransactionType.INCOME, t4.type);
        assertEquals("Pagamento", t4.category);
        assertEquals(0, t4.amount.compareTo(new BigDecimal("934.83")));
        assertEquals(LocalDate.of(2025, 11, 15), t4.date);

        TransactionData t5 = txs.get(4);
        assertEquals("Assai Atacadista Lj27", t5.description);
        assertEquals(TransactionType.EXPENSE, t5.type);
        assertEquals("Alimentação", t5.category);
        assertEquals(0, t5.amount.compareTo(new BigDecimal("120.00")));
        assertEquals(LocalDate.of(2025, 11, 21), t5.date);
        assertTrue(t5.cardName.toLowerCase().contains("virtual"));
    }
}
