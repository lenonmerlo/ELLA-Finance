package com.ella.backend.services.invoices.parsers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.ella.backend.services.invoices.util.NormalizeUtil;

public class ParserSelectionTest {

    @Test
    public void testBradescoRejectsItauPersonalite() {
        String itauText = """
            Itaú Personalité
            Lançamentos no cartão (final 8578)
            Lançamentos: compras e saques
            Resumo da fatura em R$
            """;

        BradescoInvoiceParser parser = new BradescoInvoiceParser();
        boolean result = parser.isApplicable(itauText);

        assertFalse(result, "Bradesco deve REJEITAR Itaú Personalité");
    }

    @Test
    public void testItauPersonaliteAcceptsItauText() {
        String itauText = """
            Itaú Personalité
            Lançamentos no cartão (final 8578)
            Lançamentos: compras e saques
            Resumo da fatura em R$
            Lançamentos atuais 3.760,96
            """;

        ItauPersonaliteInvoiceParser parser = new ItauPersonaliteInvoiceParser();
        boolean result = parser.isApplicable(itauText);

        assertTrue(result, "ItauPersonalite deve ACEITAR Itaú Personalité");
    }

    @Test
    public void testItauPersonaliteAcceptsWithAccents() {
        String itauText = """
            Itau Personnalité
            Lançamentos no cartão (final 8578)
            Lançamentos: compras e saques
            """;

        ItauPersonaliteInvoiceParser parser = new ItauPersonaliteInvoiceParser();
        boolean result = parser.isApplicable(itauText);

        assertTrue(result, "ItauPersonalite deve ACEITAR com acentos/variações");
    }

    @Test
    public void testBradescoAcceptsBradescoText() {
        String bradescoText = """
            Banco Bradesco S.A.
            Extrato de Conta
            Transações do período
            """;

        BradescoInvoiceParser parser = new BradescoInvoiceParser();
        boolean result = parser.isApplicable(bradescoText);

        assertTrue(result, "Bradesco deve ACEITAR Bradesco");
    }

    @Test
    public void testNormalizeUtil() {
        assertEquals("itau personalite", NormalizeUtil.normalize("Itaú Personalité"));
        assertEquals("itau personalite", NormalizeUtil.normalize("ITAU PERSONALITE"));
        assertEquals("itau personalite", NormalizeUtil.normalize("Itau   Personalité"));

        assertTrue(NormalizeUtil.containsKeyword("Itaú Personalité", "personalite"));
        assertTrue(NormalizeUtil.containsKeyword("ITAU PERSONNALITÉ", "itau"));
    }
}
