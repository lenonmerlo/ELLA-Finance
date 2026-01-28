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
            "NUBANK - Nu Pagamentos S.A.",
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
                "         ↳ Total a pagar: R$ 104,29 (valor da transação de R$ 85,00 + R$ 0,81 de IOF + R$ 18,49 de juros).",
                "",
                // variações comuns que antes eram perdidas
                "07 nov    🏪    CAPPTA *GRAU DE BEBIDA    R$ 9,00    (aprovada)",
                "07 nov    🏪    CAPPTA *GRAU DE BEBIDA    R$ 9,00",
                "08 NOV    🏪    LOJA EM 2 LINHAS",
                "R$ 12,34",
                "09 N0V    🏪    MERCADO OCR    R$ 20,00",
                "",
                // transação sem data na linha principal: data ancorada na linha anterior
                "13 NOV",
                "PARQUE VILA ITAPUA INC SPE LTD    R$ 91,70",
                "↳ Total a pagar: R$ 91,69 (valor da transação de R$ 70,00 + R$ 0,00 de IOF + R$ 21,69 de juros).",
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
        assertEquals(9, txs.size());

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
        // usa sempre o valor da linha principal
        assertEquals(0, t3.amount.compareTo(new BigDecimal("104.30")));
        assertEquals(TransactionType.EXPENSE, t3.type);
        assertEquals("Alimentação", t3.category);
        assertEquals(LocalDate.of(2025, 11, 12), t3.date);

        TransactionData t4 = txs.get(3);
        assertEquals("CAPPTA *GRAU DE BEBIDA", t4.description);
        assertEquals(0, t4.amount.compareTo(new BigDecimal("9.00")));
        assertEquals(TransactionType.EXPENSE, t4.type);
        assertNotNull(t4.category);
        assertEquals(LocalDate.of(2025, 11, 7), t4.date);

        TransactionData t5 = txs.get(4);
        assertEquals("CAPPTA *GRAU DE BEBIDA", t5.description);
        assertEquals(0, t5.amount.compareTo(new BigDecimal("9.00")));
        assertEquals(TransactionType.EXPENSE, t5.type);
        assertNotNull(t5.category);
        assertEquals(LocalDate.of(2025, 11, 7), t5.date);

        TransactionData t6 = txs.get(5);
        assertEquals("LOJA EM 2 LINHAS", t6.description);
        assertEquals(0, t6.amount.compareTo(new BigDecimal("12.34")));
        assertEquals(TransactionType.EXPENSE, t6.type);
        assertNotNull(t6.category);
        assertEquals(LocalDate.of(2025, 11, 8), t6.date);

        TransactionData t7 = txs.get(6);
        assertEquals("MERCADO OCR", t7.description);
        assertEquals(0, t7.amount.compareTo(new BigDecimal("20.00")));
        assertEquals(TransactionType.EXPENSE, t7.type);
        assertNotNull(t7.category);
        assertEquals(LocalDate.of(2025, 11, 9), t7.date);

        TransactionData t8 = txs.get(7);
        assertEquals("PARQUE VILA ITAPUA INC SPE LTD", t8.description);
        assertEquals(0, t8.amount.compareTo(new BigDecimal("91.70")));
        assertEquals(TransactionType.EXPENSE, t8.type);
        assertNotNull(t8.category);
        assertEquals(LocalDate.of(2025, 11, 13), t8.date);

        TransactionData t9 = txs.get(8);
        assertEquals("Pagamento em 05 NOV", t9.description);
        assertEquals(0, t9.amount.compareTo(new BigDecimal("934.83")));
        assertEquals(TransactionType.INCOME, t9.type);
        assertEquals("Reembolso", t9.category);
        assertEquals(LocalDate.of(2025, 11, 5), t9.date);
    }

    @Test
    void parsesThreeLineTransactionWhenTotalToPayDetailIsWrappedAcrossMultipleLines() {
        String text = String.join("\n",
                "Nubank",
                "Nu Pagamentos S.A.",
                "Esta é a sua fatura de dezembro, no valor de R$ 110,00",
                "Data de vencimento: 12 DEZ 2025",
                "TRANSAÇÕES DE 05 NOV A 05 DEZ",
                // header sem valor
                "12 NOV PARQUE VILA ITAPUA INC SPE LTD",
                // linha de detalhe (1)
                "Total a pagar: R$ 91,69 (valor da transação de R$ 75,39 + R$ 0,70 de IOF +",
                // linha de detalhe (2) - continuação
                "R$ 15,60 de juros).",
                // valor final em linha isolada
                "R$ 91,69");

        NubankInvoiceParser parser = new NubankInvoiceParser();
        assertTrue(parser.isApplicable(text));

        List<TransactionData> txs = parser.extractTransactions(text);
        assertNotNull(txs);
        assertEquals(1, txs.size());
        assertEquals("PARQUE VILA ITAPUA INC SPE LTD", txs.get(0).description);
        assertEquals(0, txs.get(0).amount.compareTo(new BigDecimal("91.69")));
        assertEquals(LocalDate.of(2025, 11, 12), txs.get(0).date);
        assertEquals(TransactionType.EXPENSE, txs.get(0).type);
    }

    @Test
    void parsesThreeLineTransactionWhenMerchantContainsWordPagamentos() {
        String text = String.join("\n",
                "Nubank",
                "Nu Pagamentos S.A.",
                "Esta é a sua fatura de dezembro, no valor de R$ 65,00",
                "Data de vencimento: 12 DEZ 2025",
                "TRANSAÇÕES DE 05 NOV A 05 DEZ",
                "12 NOV GOOGLE BRASIL PAGAMENTOS LTDA.",
                "Total a pagar: R$ 12,83 (valor da transação de R$ 10,90 + R$ 0,09 de IOF +",
                "R$ 1,85 de juros).",
                "R$ 12,84");

        NubankInvoiceParser parser = new NubankInvoiceParser();
        assertTrue(parser.isApplicable(text));

        List<TransactionData> txs = parser.extractTransactions(text);
        assertNotNull(txs);
        assertEquals(1, txs.size());
        assertEquals("GOOGLE BRASIL PAGAMENTOS LTDA.", txs.get(0).description);
        assertEquals(0, txs.get(0).amount.compareTo(new BigDecimal("12.84")));
        assertEquals(LocalDate.of(2025, 11, 12), txs.get(0).date);
        assertEquals(TransactionType.EXPENSE, txs.get(0).type);
    }
}
