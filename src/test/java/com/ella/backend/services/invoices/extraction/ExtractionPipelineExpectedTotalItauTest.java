package com.ella.backend.services.invoices.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Method;
import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class ExtractionPipelineExpectedTotalItauTest {

    private static BigDecimal invokeExtractExpectedTotal(String text) throws Exception {
        Method m = ExtractionPipeline.class.getDeclaredMethod("extractInvoiceExpectedTotal", String.class);
        m.setAccessible(true);
        Object out = m.invoke(null, text);
        return (BigDecimal) out;
    }

    @Test
    void itauPrefersCurrentInvoiceTotalOverPreviousInvoiceTotal() throws Exception {
        String text = String.join("\n",
                "Resumo da fatura em R$",
                "Total da fatura anterior 3.692,62",
                "Pagamento efetuado em 21/11/2025 -3.692,62",
                "Saldo financiado 0,00",
                "Lançamentos atuais 2.005,92",
                "Total desta fatura 2.005,92",
                "Pagamento mínimo: R$ 200,59"
        );

        BigDecimal expected = invokeExtractExpectedTotal(text);
        assertNotNull(expected);
        assertEquals(0, expected.compareTo(new BigDecimal("2005.92")));
    }

    @Test
    void genericTotalDaFaturaStillWorksWhenNotPreviousInvoice() throws Exception {
        String text = String.join("\n",
                "Total da fatura: R$ 1.234,56",
                "Vencimento 20/09/2025"
        );

        BigDecimal expected = invokeExtractExpectedTotal(text);
        assertNotNull(expected);
        assertEquals(0, expected.compareTo(new BigDecimal("1234.56")));
    }
}
