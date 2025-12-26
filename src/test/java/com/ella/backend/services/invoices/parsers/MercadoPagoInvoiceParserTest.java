package com.ella.backend.services.invoices.parsers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ella.backend.enums.TransactionType;

class MercadoPagoInvoiceParserTest {

    @Test
    void parsesDueDateAndBasicLinesWithoutCurrencySymbol() {
        String text = String.join("\n",
                "Mercado Pago",
                "Essa é sua fatura de dezembro",
                "Total a pagar",
                "R$ 2.449,67",
                "Vence em",
                "23/12/2025",
                "",
                "Lançamentos",
                "17/12 UBER TRIP 18,40",
                "18/12 PAGAMENTO DA FATURA -100,00",
                "19/12 IFD*IFD*COMERCIO DE 120,90"
        );

        MercadoPagoInvoiceParser parser = new MercadoPagoInvoiceParser();
        assertTrue(parser.isApplicable(text));

        LocalDate due = parser.extractDueDate(text);
        assertEquals(LocalDate.of(2025, 12, 23), due);

        List<TransactionData> txs = parser.extractTransactions(text);
        assertNotNull(txs);
        assertEquals(3, txs.size());

        TransactionData t1 = txs.get(0);
        assertEquals("UBER TRIP", t1.description);
        assertEquals(TransactionType.EXPENSE, t1.type);
        assertEquals(0, t1.amount.compareTo(new BigDecimal("18.40")));

        TransactionData t2 = txs.get(1);
        assertEquals("PAGAMENTO DA FATURA", t2.description);
        assertEquals(TransactionType.INCOME, t2.type);
        assertEquals(0, t2.amount.compareTo(new BigDecimal("100.00")));

        TransactionData t3 = txs.get(2);
        assertEquals("IFD*IFD*COMERCIO DE", t3.description);
        assertEquals(TransactionType.EXPENSE, t3.type);
        assertEquals(0, t3.amount.compareTo(new BigDecimal("120.90")));
    }
}
