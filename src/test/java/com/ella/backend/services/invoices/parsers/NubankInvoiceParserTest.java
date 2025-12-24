package com.ella.backend.services.invoices.parsers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ella.backend.enums.TransactionType;

class NubankInvoiceParserTest {

    @Test
    void detectsAndParsesDueDateTransactionsAndPayments() {
        String text = String.join("\n",
                "Olá, Lia.",
                "Esta é a sua fatura de dezembro, no valor de R$ 1.107,60",
                "",
                "Data de vencimento: 12 DEZ 2025",
                "Período vigente: 05 NOV a 05 DEZ",
                "Limite total do cartão de crédito: R$ 1.300,00",
                "",
                "LIA RIBEIRO ENDRINGER                    EMISSÃO E ENVIO 05 DEZ 2025",
                "FATURA 12 DEZ 2025",
                "",
                "TRANSAÇÕES    DE 05 NOV A 05 DEZ",
                "",
                "06 NOV    🔄    Pepay*Segurofatura    R$ 6,90",
                "         └→ Total e pagar: R$ 6,90 (valor da transação de R$ 6,90 + R$ 0,00 de IOF + R$ 0,00 de juros).",
                "",
                "06 NOV    💳    Uber*Trip Help.U    R$ 43,75",
                "         └→ Total e pagar: R$ 43,75 (valor da transação de R$ 43,75 + R$ 0,00 de IOF + R$ 0,00 de juros).",
                "",
                "12 NOV    🏪    R F CRUZ CHURRASCANAL    R$ 104,30",
                "         └→ Total e pagar: R$ 104,29 (valor da transação de R$ 85,00 + R$ 0,81 de IOF + R$ 18,49 de juros).",
                "",
                "Pagamentos e Financiamentos    -R$ 660,63",
                "",
                "05 NOV    Pagamento em 05 NOV    -R$ 934,83"
        );

        NubankInvoiceParser parser = new NubankInvoiceParser();
        assertTrue(parser.isApplicable(text));

        LocalDate dueDate = parser.extractDueDate(text);
        assertEquals(LocalDate.of(2025, 12, 12), dueDate);

        List<TransactionData> txs = parser.extractTransactions(text);
        assertNotNull(txs);
        assertEquals(4, txs.size());

        TransactionData t1 = txs.get(0);
        assertEquals("Pepay*Segurofatura", t1.description);
        assertEquals(0, t1.amount.compareTo(new BigDecimal("6.90")));
        assertEquals(TransactionType.EXPENSE, t1.type);
        assertEquals("Seguro", t1.category);
        assertEquals(LocalDate.of(2025, 11, 6), t1.date);

        TransactionData t2 = txs.get(1);
        assertEquals("Uber*Trip Help.U", t2.description);
        assertEquals(0, t2.amount.compareTo(new BigDecimal("43.75")));
        assertEquals(TransactionType.EXPENSE, t2.type);
        assertEquals("Transporte", t2.category);
        assertEquals(LocalDate.of(2025, 11, 6), t2.date);

        TransactionData t3 = txs.get(2);
        assertEquals("R F CRUZ CHURRASCANAL", t3.description);
        // a linha principal tem 104,30, mas o "Total e pagar" pode ajustar para 104,29
        assertEquals(0, t3.amount.compareTo(new BigDecimal("104.29")));
        assertEquals(TransactionType.EXPENSE, t3.type);
        assertEquals("Alimentação", t3.category);
        assertEquals(LocalDate.of(2025, 11, 12), t3.date);

        TransactionData t4 = txs.get(3);
        assertEquals("Pagamento em 05 NOV", t4.description);
        assertEquals(0, t4.amount.compareTo(new BigDecimal("934.83")));
        assertEquals(TransactionType.INCOME, t4.type);
        assertEquals("Reembolso", t4.category);
        assertEquals(LocalDate.of(2025, 11, 5), t4.date);
    }
}
