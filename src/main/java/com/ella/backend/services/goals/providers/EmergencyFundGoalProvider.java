package com.ella.backend.services.goals.providers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;

import com.ella.backend.entities.FinancialTransaction;
import com.ella.backend.entities.Goal;
import com.ella.backend.entities.Person;
import com.ella.backend.enums.GoalStatus;
import com.ella.backend.services.goals.GoalAnalysisUtils;

@Component
public class EmergencyFundGoalProvider implements GoalProvider {

    @Override
    public GoalDataSource getDataSource() {
        return GoalDataSource.CASHFLOW_COMBINED;
    }

    @Override
    public List<Goal> generateGoals(Person person, List<FinancialTransaction> recentTransactions) {
        if (person == null || recentTransactions == null || recentTransactions.isEmpty()) {
            return List.of();
        }

        int monthsCount = Math.max(3, Math.min(6, GoalAnalysisUtils.distinctMonths(recentTransactions).size()));

        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expenses = BigDecimal.ZERO;
        for (FinancialTransaction tx : recentTransactions) {
            if (GoalAnalysisUtils.isIncome(tx)) {
                income = income.add(GoalAnalysisUtils.safeAmount(tx));
            } else if (GoalAnalysisUtils.isExpense(tx)) {
                expenses = expenses.add(GoalAnalysisUtils.safeAmount(tx));
            }
        }

        BigDecimal avgIncome = GoalAnalysisUtils.averagePerMonth(income, monthsCount);
        BigDecimal avgExpenses = GoalAnalysisUtils.averagePerMonth(expenses, monthsCount);
        if (avgExpenses.compareTo(BigDecimal.valueOf(500)) < 0) {
            return List.of();
        }

        int monthsOfFund = avgIncome.compareTo(avgExpenses.multiply(BigDecimal.valueOf(1.20))) >= 0 ? 6 : 3;
        BigDecimal fundTarget = avgExpenses.multiply(BigDecimal.valueOf(monthsOfFund)).setScale(2, RoundingMode.HALF_UP);

        BigDecimal suggestedMonthly;
        if (avgIncome.compareTo(BigDecimal.ZERO) > 0) {
            suggestedMonthly = avgIncome.multiply(BigDecimal.valueOf(0.10)).setScale(2, RoundingMode.HALF_UP);
        } else {
            suggestedMonthly = avgExpenses.multiply(BigDecimal.valueOf(0.05)).setScale(2, RoundingMode.HALF_UP);
        }
        if (suggestedMonthly.compareTo(BigDecimal.valueOf(50)) < 0) {
            suggestedMonthly = BigDecimal.valueOf(50);
        }

        int monthsToReach = (int) Math.ceil(fundTarget.divide(suggestedMonthly, 6, RoundingMode.HALF_UP).doubleValue());
        monthsToReach = Math.max(6, Math.min(24, monthsToReach));

        Goal goal = new Goal();
        goal.setOwner(person);
        goal.setStatus(GoalStatus.ACTIVE);
        goal.setCurrentAmount(BigDecimal.ZERO);
        goal.setDeadline(LocalDate.now().plusMonths(monthsToReach));

        goal.setTitle("Criar fundo de emergência");
        goal.setDescription(String.format(
                "Você gasta em média %s/mês. Um fundo de emergência recomendado é de %d meses (%s). Sugestão: reservar %s por mês para construir esse fundo.",
                GoalAnalysisUtils.formatCurrency(avgExpenses),
                monthsOfFund,
                GoalAnalysisUtils.formatCurrency(fundTarget),
                GoalAnalysisUtils.formatCurrency(suggestedMonthly)
        ));
        goal.setTargetAmount(fundTarget);

        return List.of(goal);
    }

    @Override
    public int getPriority() {
        return 1;
    }
}
