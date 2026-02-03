package com.ella.backend.services.insights.providers;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.ella.backend.dto.dashboard.InsightDTO;
import com.ella.backend.entities.FinancialTransaction;
import com.ella.backend.entities.Person;
import com.ella.backend.services.insights.InsightDataCache;
import com.ella.backend.services.insights.InsightUtils;

@Component
@Order(5)
public class CashflowSummaryInsightProvider implements InsightProvider {

    private final InsightDataCache insightDataCache;

    public CashflowSummaryInsightProvider(InsightDataCache insightDataCache) {
        this.insightDataCache = insightDataCache;
    }

    @Override
    public List<InsightDTO> generate(Person person, int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        List<FinancialTransaction> txs = insightDataCache.getCashflowTransactionsForMonth(person, ym);
        if (txs == null || txs.isEmpty()) {
            return List.of();
        }

        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expenses = BigDecimal.ZERO;

        for (FinancialTransaction tx : txs) {
            if (tx == null) {
                continue;
            }
            if (InsightUtils.isIncome(tx)) {
                income = income.add(InsightUtils.safeAmount(tx));
            } else if (InsightUtils.isExpense(tx)) {
                expenses = expenses.add(InsightUtils.safeAmount(tx));
            }
        }

        if (income.compareTo(BigDecimal.ZERO) <= 0 && expenses.compareTo(BigDecimal.ZERO) <= 0) {
            return List.of();
        }

        BigDecimal net = income.subtract(expenses);
        String type = net.compareTo(BigDecimal.ZERO) < 0 ? "warning" : "info";

        String message = String.format(
                "Fluxo de caixa do mês: entradas %s, saídas %s, resultado %s.",
                InsightUtils.formatCurrency(income),
                InsightUtils.formatCurrency(expenses),
                InsightUtils.formatCurrency(net)
        );

        return List.of(InsightDTO.builder()
                .type(type)
                .category("Conta corrente")
                .message(message)
                .build())
                .stream()
                .filter(Objects::nonNull)
                .toList();
    }
}
