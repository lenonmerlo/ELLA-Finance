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
        // "Inclusao de Pagamento" deve ser ignorado (pagamento de fatura anterior/adiantamento)
        assertEquals(3, txs.size());

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
    }

    @Test
    void returnsFalseWhenNotApplicable() {
        C6InvoiceParser parser = new C6InvoiceParser();
        assertFalse(parser.isApplicable("BANCO X\nVencimento: 20/12/2025"));
    }

    @Test
    void parsesDueDateWhenYearIsMissingUsingInferredYear() {
        String text = String.join("\n",
                "C6 BANK",
                "Emissão: 01/12/2025",
                "Vencimento: 20/12",
                "Transações do cartão principal",
                "C6 Carbon Virtual Final 5867 - LENON MERLO",
                "14 nov   BAR PIMENTA CARIOCA   92,40"
        );

        C6InvoiceParser parser = new C6InvoiceParser();
        assertTrue(parser.isApplicable(text));
        assertEquals(LocalDate.of(2025, 12, 20), parser.extractDueDate(text));
    }

    @Test
    void parsesDueDateWhenMonthIsTextual() {
        String text = String.join("\n",
                "C6 BANK",
                "Vencimento: 20 DEZ 2025",
                "C6 Carbon Virtual Final 5867 - LENON MERLO"
        );

        C6InvoiceParser parser = new C6InvoiceParser();
        assertTrue(parser.isApplicable(text));
        assertEquals(LocalDate.of(2025, 12, 20), parser.extractDueDate(text));
    }

    @Test
    void extractsTransactionsWithNumberedDescriptionsAndOcrMonthDigits() {
        String text = String.join("\n",
                "C6 BANK",
                "Vencimento: 20/12/2025",
                "Transações",
                "C6 Carbon Virtual Final 5867 - LENON MERLO",
                "21 n0v   59146329HEBER   52,00"
        );

        C6InvoiceParser parser = new C6InvoiceParser();
        List<TransactionData> txs = parser.extractTransactions(text);

        assertEquals(1, txs.size());
        TransactionData t = txs.get(0);
        assertEquals("59146329HEBER", t.description);
        assertEquals(0, t.amount.compareTo(new BigDecimal("52.00")));
        assertEquals(TransactionType.EXPENSE, t.type);
        assertEquals(LocalDate.of(2025, 11, 21), t.date);
    }

    @Test
    void skipsSummarySectionLinesThatLookLikeTransactions() {
        String text = String.join("\n",
                "C6 BANK",
                "Vencimento: 20/12/2025",
                "Resumo da fatura",
                "21 nov   Compras nacionais   5.098,40",
                "Transações",
                "C6 Carbon Virtual Final 5867 - LENON MERLO",
                "14 nov   BAR PIMENTA CARIOCA   92,40"
        );

        C6InvoiceParser parser = new C6InvoiceParser();
        List<TransactionData> txs = parser.extractTransactions(text);
        assertEquals(1, txs.size());
        assertEquals("BAR PIMENTA CARIOCA", txs.get(0).description);
    }

    @Test
    void keepsDuplicatesWhenSameCardSectionRepeats() {
        String text = String.join("\n",
                "C6 BANK",
                "Vencimento: 20/12/2025",
                "Transações",
                "C6 Carbon Virtual Final 5867 - LENON MERLO",
                "14 nov   BAR PIMENTA CARIOCA   92,40",
                // Repete o mesmo cartão (ex.: página duplicada)
                "C6 Carbon Virtual Final 5867 - LENON MERLO",
                "14 nov   BAR PIMENTA CARIOCA   92,40"
        );

        C6InvoiceParser parser = new C6InvoiceParser();
        List<TransactionData> txs = parser.extractTransactions(text);
        assertEquals(2, txs.size());
        assertEquals("BAR PIMENTA CARIOCA", txs.get(0).description);
        assertEquals("BAR PIMENTA CARIOCA", txs.get(1).description);
    }

    @Test
    void doesNotDeduplicateSameDescriptionWithDifferentAmounts() {
        String text = String.join("\n",
                "C6 BANK",
                "Vencimento: 20/12/2025",
                "Transações",
                "C6 Carbon Virtual Final 5867 - LENON MERLO",
                "21 nov   BAR PIMENTA CARIOCA   11,00",
                "21 nov   BAR PIMENTA CARIOCA   11,00",
                "21 nov   BAR PIMENTA CARIOCA   18,00"
        );

        C6InvoiceParser parser = new C6InvoiceParser();
        List<TransactionData> txs = parser.extractTransactions(text);

        assertEquals(3, txs.size());
        assertEquals(0, txs.get(0).amount.compareTo(new BigDecimal("11.00")));
        assertEquals(0, txs.get(1).amount.compareTo(new BigDecimal("11.00")));
        assertEquals(0, txs.get(2).amount.compareTo(new BigDecimal("18.00")));
    }

    @Test
    void infersPreviousYearWhenTransactionMonthIsAfterDueMonth() {
        String text = String.join("\n",
                "C6 BANK",
                "Vencimento: 19/02/2026",
                "Transações",
                "C6 Carbon Virtual Final 5867 - LENON MERLO",
                "27 dez   AIRBNB * HMF99EFWK9   369,48",
                "02 fev   SHOPEE *LIVICOMPANY   66,41"
        );

        C6InvoiceParser parser = new C6InvoiceParser();
        List<TransactionData> txs = parser.extractTransactions(text);

        assertEquals(2, txs.size());
        assertEquals(LocalDate.of(2025, 12, 27), txs.get(0).date);
        assertEquals(LocalDate.of(2026, 2, 2), txs.get(1).date);
    }
}
