package com.ella.backend.services.invoices.parsers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ella.backend.enums.TransactionType;

class C6InvoiceParserTest {

    @Test
    void detectsAndParsesDueDateAndTransactions() {
        String text = String.join("\n",
                "C6 BANK",
                "Olá, Lenon! Sua fatura com vencimento em Dezembro chegou no valor de R$ 5.098,40",
                "Vencimento: 20/12/2025",
                "Transações do cartão principal",
                "C6 Carbon Virtual Final 5867 - LENON MERLO",
                "27 out   AIRBNB * HMF99EFWK9 - Parcela 2/3   369,48",
                "14 nov   BAR PIMENTA CARIOCA   92,40",
                "09 dez   Estorno Tarifa - Estorno   98,00",
                "C6 Carbon Virtual Final 1234 - OUTRO TITULAR",
                "21 nov   Inclusao de Pagamento   5.698,02"
        );

        C6InvoiceParser parser = new C6InvoiceParser();

        assertTrue(parser.isApplicable(text));

        LocalDate dueDate = parser.extractDueDate(text);
        assertEquals(LocalDate.of(2025, 12, 20), dueDate);

        List<TransactionData> txs = parser.extractTransactions(text);
        assertEquals(4, txs.size());

        TransactionData t1 = txs.get(0);
        assertEquals("AIRBNB * HMF99EFWK9", t1.description);
        assertEquals(0, t1.amount.compareTo(new BigDecimal("369.48")));
        assertEquals(TransactionType.EXPENSE, t1.type);
        assertEquals("Hospedagem", t1.category);
        assertEquals(LocalDate.of(2025, 10, 27), t1.date);
        assertEquals("Carbon Virtual 5867", t1.cardName);
        assertEquals(2, t1.installmentNumber);
        assertEquals(3, t1.installmentTotal);

        TransactionData t2 = txs.get(1);
        assertEquals("BAR PIMENTA CARIOCA", t2.description);
        assertEquals(0, t2.amount.compareTo(new BigDecimal("92.40")));
        assertEquals(TransactionType.EXPENSE, t2.type);
        assertEquals("Lazer", t2.category);
        assertEquals(LocalDate.of(2025, 11, 14), t2.date);
        assertEquals("Carbon Virtual 5867", t2.cardName);
        assertNull(t2.installmentNumber);
        assertNull(t2.installmentTotal);

        TransactionData t3 = txs.get(2);
        assertEquals("Estorno Tarifa - Estorno", t3.description);
        assertEquals(0, t3.amount.compareTo(new BigDecimal("98.00")));
        assertEquals(TransactionType.INCOME, t3.type);
        assertEquals("Reembolso", t3.category);
        assertEquals(LocalDate.of(2025, 12, 9), t3.date);
        assertEquals("Carbon Virtual 5867", t3.cardName);

        TransactionData t4 = txs.get(3);
        assertEquals("Inclusao de Pagamento", t4.description);
        assertEquals(0, t4.amount.compareTo(new BigDecimal("5698.02")));
        assertEquals(TransactionType.INCOME, t4.type);
        assertEquals("Pagamento", t4.category);
        assertEquals(LocalDate.of(2025, 11, 21), t4.date);
        assertEquals("Carbon Virtual 1234", t4.cardName);
    }

    @Test
    void returnsFalseWhenNotApplicable() {
        C6InvoiceParser parser = new C6InvoiceParser();
        assertFalse(parser.isApplicable("BANCO X\nVencimento: 20/12/2025"));
    }
}
