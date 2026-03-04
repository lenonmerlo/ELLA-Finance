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

    @Test
    void c6IgnoresInstallmentTotalAPagarAndKeepsInvoiceTotal() throws Exception {
        String text = String.join("\n",
                "Olá, Lenon! Sua fatura com vencimento em Dezembro chegou no valor de R$ 5.098,40.",
                "Valor da fatura: R$ 5.098,40",
                "Parcelamento Total a pagar CET",
                "R$ 7.012,90 Entrada + 9x de R$ 701,29 152,52% a.a.",
                "Total a pagar R$ 5.098,40"
        );

        BigDecimal expected = invokeExtractExpectedTotal(text);
        assertNotNull(expected);
        assertEquals(0, expected.compareTo(new BigDecimal("5098.40")));
    }

    @Test
    void c6DoesNotCaptureDayFromDueDateAsInvoiceTotal() throws Exception {
        String text = String.join("\n",
                "Valor da fatura:",
                "Vencimento:",
                "20/12/2025",
                "R$ 5.098,40",
                "Parcelamento Total a pagar CET",
                "R$ 7.012,90 Entrada + 9x de R$ 701,29"
        );

        BigDecimal expected = invokeExtractExpectedTotal(text);
        assertNotNull(expected);
        assertEquals(0, expected.compareTo(new BigDecimal("5098.40")));
    }

    @Test
    void sicrediPrefersInvoiceTotalOverCreditLimit() throws Exception {
        String text = String.join("\n",
                "Total fatura de janeiro R$ 9.900,16",
                "Limite total de crédito R$ 100.000,00",
                "Resumo da fatura",
                "Total desta Fatura 9.900,16",
                "Limite em 11/01",
                "Total R$ 25.000,00 R$ 100.000,00"
        );

        BigDecimal expected = invokeExtractExpectedTotal(text);
        assertNotNull(expected);
        assertEquals(0, expected.compareTo(new BigDecimal("9900.16")));
    }
}
