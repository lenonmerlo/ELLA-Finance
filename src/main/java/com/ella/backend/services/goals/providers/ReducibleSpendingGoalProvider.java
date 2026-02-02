package com.ella.backend.services.goals.providers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.ella.backend.entities.FinancialTransaction;
import com.ella.backend.entities.Goal;
import com.ella.backend.entities.Person;
import com.ella.backend.enums.GoalStatus;
import com.ella.backend.services.goals.GoalAnalysisUtils;

@Component
public class ReducibleSpendingGoalProvider implements GoalProvider {

    @Override
    public List<Goal> generateGoals(Person person, List<FinancialTransaction> recentTransactions) {
        if (person == null || recentTransactions == null || recentTransactions.isEmpty()) {
            return List.of();
        }

        List<YearMonth> months = GoalAnalysisUtils.distinctMonths(recentTransactions);
        int monthsCount = Math.max(3, Math.min(6, months.size()));

        Map<String, BigDecimal> totals = GoalAnalysisUtils.totalExpensesByCategory(recentTransactions);

        Map.Entry<String, BigDecimal> topReducible = totals.entrySet().stream()
                .filter(e -> e.getKey() != null && !e.getKey().isBlank())
                .filter(e -> GoalAnalysisUtils.isReducibleCategory(e.getKey()))
                .filter(e -> e.getValue() != null && e.getValue().compareTo(BigDecimal.ZERO) > 0)
                .max(Map.Entry.comparingByValue())
                .orElse(null);

        if (topReducible == null) {
            return List.of();
        }

        String category = topReducible.getKey();
        BigDecimal avgMonthly = GoalAnalysisUtils.averagePerMonth(topReducible.getValue(), monthsCount);

        if (avgMonthly.compareTo(BigDecimal.valueOf(150)) < 0) {
            return List.of();
        }

        BigDecimal reductionPct = BigDecimal.valueOf(0.10);
        BigDecimal targetMonthly = avgMonthly
                .multiply(BigDecimal.ONE.subtract(reductionPct))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal savings = avgMonthly.subtract(targetMonthly).setScale(2, RoundingMode.HALF_UP);

        if (savings.compareTo(BigDecimal.valueOf(30)) < 0) {
            return List.of();
        }

        Goal goal = new Goal();
        goal.setOwner(person);
        goal.setStatus(GoalStatus.ACTIVE);
        goal.setDeadline(LocalDate.now().plusMonths(3));
        goal.setCurrentAmount(BigDecimal.ZERO);

        goal.setTitle(String.format("Reduzir gastos em %s", category));
        goal.setDescription(String.format(
                "Você gastou em média %s/mês em %s nos últimos meses. Tente reduzir para %s/mês (economia de ~%s/mês) pelos próximos 3 meses.",
                GoalAnalysisUtils.formatCurrency(avgMonthly),
                category,
                GoalAnalysisUtils.formatCurrency(targetMonthly),
                GoalAnalysisUtils.formatCurrency(savings)
        ));
        goal.setTargetAmount(targetMonthly);

        return List.of(goal);
    }

    @Override
    public int getPriority() {
        return 3;
    }
}
