package com.ella.backend.services;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.ella.backend.dto.reports.CategoryTotalDTO;
import com.ella.backend.dto.reports.ReportResponseDTO;

final class FinancialReportTemplates {

    private FinancialReportTemplates() {}

    static String toPdfHtml(ReportResponseDTO report) {
        StringBuilder sb = new StringBuilder(8_000);
        sb.append("<!DOCTYPE html><html><head><meta charset='utf-8'/>");
        sb.append("<style>")
                .append("body{font-family:Arial,sans-serif;font-size:12px;color:#111;margin:24px;}")
                .append("h1{font-size:18px;margin:0 0 6px 0;}")
                .append("h2{font-size:14px;margin:18px 0 8px 0;}")
                .append(".muted{color:#555;}")
                .append(".grid{display:flex;gap:10px;}")
                .append(".card{border:1px solid #ddd;border-radius:8px;padding:10px;flex:1;}")
                .append("table{width:100%;border-collapse:collapse;}")
                .append("th,td{border-bottom:1px solid #eee;padding:6px 4px;text-align:left;}")
                .append("th{text-transform:uppercase;font-size:10px;color:#555;}")
                .append(".right{text-align:right;}")
                .append("</style>");
        sb.append("</head><body>");

        sb.append("<h1>").append(escape(report.getTitle())).append("</h1>");
        sb.append("<div class='muted'>Período: ")
                .append(escape(String.valueOf(report.getPeriodStart())))
                .append(" a ")
                .append(escape(String.valueOf(report.getPeriodEnd())))
                .append("</div>");

        sb.append("<h2>Resumo</h2>");
        sb.append("<div class='grid'>");
        sb.append(card("Receitas", money(report.getSummary() != null ? report.getSummary().getTotalIncome() : null)));
        sb.append(card("Despesas", money(report.getSummary() != null ? report.getSummary().getTotalExpenses() : null)));
        sb.append(card("Saldo", money(report.getSummary() != null ? report.getSummary().getBalance() : null)));
        sb.append("</div>");

        sb.append("<h2>Gastos por categoria</h2>");
        sb.append(categoryTable(report.getExpensesByCategory()));

        sb.append("<h2>Receitas por categoria</h2>");
        sb.append(categoryTable(report.getIncomesByCategory()));

        sb.append("<h2>Orçamento</h2>");
        sb.append(budgetSection(report.getBudget()));

        sb.append("<h2>Movimentação C/C</h2>");
        sb.append(bankStatementsSection(report.getBankStatements()));

        sb.append("</body></html>");
        return sb.toString();
    }

    private static String budgetSection(Map<String, Object> budget) {
        if (budget == null || budget.isEmpty()) {
            return "<div class='muted'>Sem orçamento configurado.</div>";
        }
        Object configured = budget.get("configured");
        if (!(configured instanceof Boolean) || !((Boolean) configured)) {
            return "<div class='muted'>Sem orçamento configurado.</div>";
        }

        StringBuilder sb = new StringBuilder(1200);
        sb.append("<div class='grid'>");
        sb.append(card("Renda (planejada)", money(toBigDecimal(budget.get("income")))));
        sb.append(card("Total (planejado)", money(toBigDecimal(budget.get("total")))));
        sb.append(card("Saldo (planejado)", money(toBigDecimal(budget.get("balance")))));
        sb.append("</div>");

        sb.append("<table><thead><tr><th>Grupo</th><th class='right'>Valor</th><th class='right'>%</th></tr></thead><tbody>");
        sb.append(row(
                "Necessidades (Essencial + Necessário)",
                money(sum(toBigDecimal(budget.get("essentialFixedCost")), toBigDecimal(budget.get("necessaryFixedCost")))),
                percent(toBigDecimal(budget.get("necessitiesPercentage")))
        ));
        sb.append(row(
                "Desejos (Variável)",
                money(toBigDecimal(budget.get("variableFixedCost"))),
                percent(toBigDecimal(budget.get("desiresPercentage")))
        ));
        sb.append(row(
                "Investimentos (Inv + Compra + Proteção)",
                money(sum(toBigDecimal(budget.get("investment")), sum(toBigDecimal(budget.get("plannedPurchase")), toBigDecimal(budget.get("protection"))))),
                percent(toBigDecimal(budget.get("investmentsPercentage")))
        ));
        sb.append("</tbody></table>");
        return sb.toString();
    }

    private static String bankStatementsSection(Map<String, Object> bankStatements) {
        if (bankStatements == null || bankStatements.isEmpty()) {
            return "<div class='muted'>Sem dados de conta corrente no período.</div>";
        }

        Map<String, Object> summary = asMap(bankStatements.get("summary"));
        List<Map<String, Object>> txs = asListOfMaps(bankStatements.get("transactions"));

        StringBuilder sb = new StringBuilder(2500);
        sb.append("<div class='grid'>");
        sb.append(card("Entradas", money(toBigDecimal(summary.get("totalIncome")))));
        sb.append(card("Saídas", money(toBigDecimal(summary.get("totalExpenses")))));
        sb.append(card("Saldo do período", money(toBigDecimal(summary.get("balance")))));
        sb.append("</div>");

        sb.append("<div class='muted'>Transações (amostra)</div>");
        sb.append("<table><thead><tr><th>Data</th><th>Descrição</th><th class='right'>Valor</th><th class='right'>Saldo</th></tr></thead><tbody>");
        if (txs.isEmpty()) {
            sb.append("<tr><td colspan='4' class='muted'>Sem transações no período.</td></tr>");
        } else {
            int limit = Math.min(txs.size(), 40);
            for (int i = 0; i < limit; i++) {
                Map<String, Object> t = txs.get(i);
                sb.append("<tr><td>")
                        .append(escape(String.valueOf(t.getOrDefault("transactionDate", ""))))
                        .append("</td><td>")
                        .append(escape(String.valueOf(t.getOrDefault("description", ""))))
                        .append("</td><td class='right'>")
                        .append(escape(money(toBigDecimal(t.get("amount")))))
                        .append("</td><td class='right'>")
                        .append(escape(money(toBigDecimal(t.get("balance")))))
                        .append("</td></tr>");
            }
        }
        sb.append("</tbody></table>");
        return sb.toString();
    }

    private static Map<String, Object> asMap(Object v) {
        if (v instanceof Map<?, ?> m) {
            @SuppressWarnings("unchecked")
            Map<String, Object> out = (Map<String, Object>) m;
            return out;
        }
        return Collections.emptyMap();
    }

    private static List<Map<String, Object>> asListOfMaps(Object v) {
        if (v instanceof List<?> list) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> mm = (Map<String, Object>) m;
                    out.add(mm);
                }
            }
            return out;
        }
        return List.of();
    }

    private static String row(String label, String value, String pct) {
        return "<tr><td>" + escape(label) + "</td><td class='right'>" + escape(value) + "</td><td class='right'>" + escape(pct) + "</td></tr>";
    }

    private static BigDecimal toBigDecimal(Object v) {
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        if (v instanceof String s) {
            try {
                return new BigDecimal(s);
            } catch (Exception ignored) {
                return BigDecimal.ZERO;
            }
        }
        return BigDecimal.ZERO;
    }

    private static BigDecimal sum(BigDecimal a, BigDecimal b) {
        return (a == null ? BigDecimal.ZERO : a).add(b == null ? BigDecimal.ZERO : b);
    }

    private static String percent(BigDecimal v) {
        if (v == null) return "0%";
        return v.setScale(2, java.math.RoundingMode.HALF_UP) + "%";
    }

    private static String card(String title, String value) {
        return "<div class='card'><div class='muted'>" + escape(title) + "</div><div style='font-size:16px;font-weight:700'>" + escape(value) + "</div></div>";
    }

    private static String categoryTable(List<CategoryTotalDTO> rows) {
        StringBuilder sb = new StringBuilder(2_000);
        sb.append("<table><thead><tr><th>Categoria</th><th class='right'>Valor</th><th class='right'>%</th></tr></thead><tbody>");
        if (rows == null || rows.isEmpty()) {
            sb.append("<tr><td colspan='3' class='muted'>Sem dados no período.</td></tr>");
        } else {
            for (CategoryTotalDTO r : rows) {
                sb.append("<tr><td>")
                        .append(escape(String.valueOf(r.getCategory())))
                        .append("</td><td class='right'>")
                        .append(escape(money(r.getAmount())))
                        .append("</td><td class='right'>")
                        .append(escape(r.getPercent() == null ? "0" : r.getPercent().toPlainString()))
                        .append("%</td></tr>");
            }
        }
        sb.append("</tbody></table>");
        return sb.toString();
    }

    private static String money(BigDecimal v) {
        if (v == null) return "R$ 0,00";
        // Simple formatting for PDF; frontend does proper locale formatting.
        return "R$ " + v.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
