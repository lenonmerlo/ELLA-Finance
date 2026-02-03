package com.ella.backend.services.cashflow;

import java.util.Locale;
import java.util.Objects;

import com.ella.backend.entities.BankStatementTransaction;

public final class BankStatementCashflowHeuristics {

    private static final Locale LOCALE_PT_BR = Locale.of("pt", "BR");

    private BankStatementCashflowHeuristics() {
    }

    public static boolean shouldIgnore(BankStatementTransaction stx) {
        if (stx == null || stx.getType() == null) {
            return true;
        }
        if (stx.getType() == BankStatementTransaction.Type.BALANCE) {
            return true;
        }
        String description = stx.getDescription() != null ? stx.getDescription().trim() : "";
        return looksLikeCreditCardBillPayment(stx.getType(), description);
    }

    public static boolean looksLikeCreditCardBillPayment(BankStatementTransaction.Type txType, String description) {
        if (txType != BankStatementTransaction.Type.DEBIT || description == null) {
            return false;
        }
        String d = description.toLowerCase(LOCALE_PT_BR);

        boolean mentionsFatura = d.contains("fatura") || d.contains("fatura do") || d.contains("pagamento fatura");
        boolean mentionsCard = d.contains("cartao") || d.contains("cartão") || d.contains("credito") || d.contains("crédito");

        if (mentionsFatura && (mentionsCard || d.contains("pagamento"))) {
            return true;
        }
        return false;
    }

    public static String categorize(String description, BankStatementTransaction.Type txType) {
        if (description == null || description.isBlank()) {
            return "Outros";
        }

        String d = description.toLowerCase(LOCALE_PT_BR);

        if (txType == BankStatementTransaction.Type.CREDIT) {
            if (containsAny(d, "salario", "salário", "provento", "folha", "13o", "13º")) {
                return "Renda";
            }
        }

        if (containsAny(d, "netflix", "spotify", "disney", "prime", "hbo", "amazon", "apple", "google", "microsoft", "adobe", "icloud")) {
            return "Assinaturas";
        }
        if (containsAny(d, "ifood", "i-food", "restaurante", "lanchonete", "padaria", "supermerc", "mercado", "carrefour", "extra", "assai", "atacad")) {
            return "Alimentação";
        }
        if (containsAny(d, "uber", "99", "posto", "combust", "gasolina", "metro", "metrô", "onibus", "ônibus", "passagem")) {
            return "Transporte";
        }
        if (containsAny(d, "aluguel", "condominio", "condomínio", "iptu", "imobili")) {
            return "Moradia";
        }
        if (containsAny(d, "farmacia", "farmácia", "droga", "hospital", "clinica", "clínica", "medic")) {
            return "Saúde";
        }
        if (containsAny(d, "internet", "energia", "luz", "agua", "água", "gas", "gás", "vivo", "claro", "tim", "oi")) {
            return "Utilidades";
        }
        if (containsAny(d, "tarifa", "taxa", "iof", "anuidade", "multa", "juros")) {
            return "Taxas";
        }
        if (containsAny(d, "aplic", "invest", "cdb", "tesouro", "poup", "previd")) {
            return "Investimentos";
        }

        if (containsAny(d, "pix", "transfer", "ted", "doc")) {
            return "Outros";
        }

        return "Outros";
    }

    private static boolean containsAny(String haystack, String... needles) {
        if (haystack == null || needles == null) {
            return false;
        }
        return java.util.Arrays.stream(needles)
                .filter(Objects::nonNull)
                .anyMatch(n -> !n.isBlank() && haystack.contains(n));
    }
}
