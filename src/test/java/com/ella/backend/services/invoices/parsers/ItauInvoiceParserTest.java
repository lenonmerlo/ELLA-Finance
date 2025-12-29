package com.ella.backend.services.invoices.parsers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

class ItauInvoiceParserTest {

    @Test
    void extractsDueDateFromVencAbbrevWithoutYearByInferringYearFromOtherDates() {
        String text = String.join("\n",
                "ITAU",
                "Resumo da fatura em R$",
                "Pagamento efetuado em 21/11/2025 -3.692,62",
                "VENC. 15/12",
                "",
                "Pagamentos efetuados",
                "21/11/2025 PAGAMENTO EFETUADO -3.692,62",
                "",
                "Lançamentos: compras e saques",
                "17/11 UBER TRIP 18,40",
                "18/11 IFD*IFD*COMERCIO DE 120,90"
        );

        ItauInvoiceParser parser = new ItauInvoiceParser();
        assertTrue(parser.isApplicable(text));

        LocalDate dueDate = parser.extractDueDate(text);
        assertEquals(LocalDate.of(2025, 12, 15), dueDate);

        List<TransactionData> txs = parser.extractTransactions(text);
        assertNotNull(txs);
        assertTrue(txs.size() >= 2);
    }

    @Test
    void extractsDueDateFromVctoWithFullYear() {
        String text = String.join("\n",
                "Banco Itaú",
                "Pagamentos efetuados",
                "",
                "Lançamentos: compras e saques",
            "VCTO: 20 / 12 / 2025",
                "17/11 UBER TRIP 18,40"
        );

        ItauInvoiceParser parser = new ItauInvoiceParser();
        assertTrue(parser.isApplicable(text));

        LocalDate dueDate = parser.extractDueDate(text);
        assertEquals(LocalDate.of(2025, 12, 20), dueDate);
    }

    @Test
    void extractsDueDateFromComVencimentoEm() {
        String text = String.join("\n",
                "ItauUniclass",
                "Resumo da fatura em R$",
                "Com vencimento em:",
                "22/12/2025",
                "Pagamentos efetuados",
                "",
                "Lançamentos: compras e saques",
                "17/11 UBER TRIP 18,40"
        );

        ItauInvoiceParser parser = new ItauInvoiceParser();
        assertTrue(parser.isApplicable(text));

        LocalDate dueDate = parser.extractDueDate(text);
        assertEquals(LocalDate.of(2025, 12, 22), dueDate);
    }

    @Test
    void extractsDueDateWhenDigitsAreSeparatedBySpaces() {
        String text = String.join("\n",
                "Itau",
                "Pagamentos efetuados",
                "",
                "Lançamentos: compras e saques",
                "Vencimento: 2 2 / 1 2 / 2 0 2 5",
                "17/11 UBER TRIP 18,40"
        );

        ItauInvoiceParser parser = new ItauInvoiceParser();
        assertTrue(parser.isApplicable(text));

        LocalDate dueDate = parser.extractDueDate(text);
        assertEquals(LocalDate.of(2025, 12, 22), dueDate);
    }

    @Test
    void prefersComVencimentoEmOverProcessingVencimentoWhenBothExist() {
        String text = String.join("\n",
                "Banco Itau",
                "Postagem: 14/12/2025",
                "Processamento: 14/12/2025",
                "Entrada: 14/12/2025",
                // bloco de processamento (não é o vencimento da fatura atual)
                "Vencimento: 14/01/2026",
                "Próxima postagem: 14/01/2026",
                "",
                "O total da sua fatura é:",
                "Com vencimento em:",
                "22/12/2025",
                "",
                "Pagamentos efetuados",
                "21/11  PAGAMENTO DEBITADO AUTOMATICAMENTE  -3.692,62",
                "",
                "Lançamentos: compras e saques",
                "15/10  CLINICA SCHUNK 2025  720,00"
        );

        ItauInvoiceParser parser = new ItauInvoiceParser();
        assertTrue(parser.isApplicable(text));

        LocalDate dueDate = parser.extractDueDate(text);
        assertEquals(LocalDate.of(2025, 12, 22), dueDate);
    }
}
