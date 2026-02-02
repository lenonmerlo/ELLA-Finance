package com.ella.backend.services.goals.providers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.ella.backend.entities.FinancialTransaction;
import com.ella.backend.entities.Goal;
import com.ella.backend.entities.Person;
import com.ella.backend.enums.GoalStatus;
import com.ella.backend.services.goals.GoalAnalysisUtils;

@Component
public class BudgetOptimizationGoalProvider implements GoalProvider {

    @Override
    public List<Goal> generateGoals(Person person, List<FinancialTransaction> recentTransactions) {
        if (person == null || recentTransactions == null || recentTransactions.isEmpty()) {
            return List.of();
        }

        List<YearMonth> months = GoalAnalysisUtils.distinctMonths(recentTransactions);
        int monthsCount = Math.max(3, Math.min(6, months.size()));

        Map<String, BigDecimal> totals = GoalAnalysisUtils.totalExpensesByCategory(recentTransactions);

        record CatVar(String category, BigDecimal mean, double cv) {
        }

        List<CatVar> candidates = totals.entrySet().stream()
                .filter(e -> e.getKey() != null && !e.getKey().isBlank())
                .filter(e -> GoalAnalysisUtils.isReducibleCategory(e.getKey()))
                .map(e -> {
                    String cat = e.getKey();
                    Map<YearMonth, BigDecimal> monthly = GoalAnalysisUtils.monthlyExpensesForCategory(recentTransactions, cat);
                    List<BigDecimal> series = months.stream()
                            .map(m -> monthly.getOrDefault(m, BigDecimal.ZERO))
                            .toList();
                    double mean = GoalAnalysisUtils.mean(series);
                    double std = GoalAnalysisUtils.stdDevSample(series, mean);
                    double cv = mean <= 0.0 ? 0.0 : (std / mean);
                    return new CatVar(cat, BigDecimal.valueOf(mean).setScale(2, RoundingMode.HALF_UP), cv);
                })
                .filter(v -> v.mean().compareTo(BigDecimal.valueOf(120)) >= 0)
                .filter(v -> v.cv() >= 0.35)
                .sorted(Comparator.comparingDouble(CatVar::cv).reversed())
                .limit(2)
                .toList();

        if (candidates.isEmpty()) {
            return List.of();
        }

        List<Goal> out = new ArrayList<>();
        for (CatVar v : candidates) {
            BigDecimal budget = v.mean().multiply(BigDecimal.valueOf(1.10)).setScale(2, RoundingMode.HALF_UP);

            Goal goal = new Goal();
            goal.setOwner(person);
            goal.setStatus(GoalStatus.ACTIVE);
            goal.setCurrentAmount(BigDecimal.ZERO);
            goal.setDeadline(LocalDate.now().plusMonths(3));

            goal.setTitle(String.format("Orçamento: %s", v.category()));
            goal.setDescription(String.format(
                    "Seu gasto em %s varia bastante mês a mês. Nos últimos %d meses, a média foi %s. Sugestão: definir um orçamento de %s (média + 10%%) para manter controle.",
                    v.category(),
                    monthsCount,
                    GoalAnalysisUtils.formatCurrency(v.mean()),
                    GoalAnalysisUtils.formatCurrency(budget)
            ));
            goal.setTargetAmount(budget);
            out.add(goal);
        }
        return out;
    }

    @Override
    public int getPriority() {
        return 4;
    }
}
