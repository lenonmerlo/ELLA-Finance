package com.ella.backend.services.goals.providers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.ella.backend.entities.FinancialTransaction;
import com.ella.backend.entities.Goal;
import com.ella.backend.entities.Invoice;
import com.ella.backend.entities.Person;
import com.ella.backend.enums.GoalStatus;
import com.ella.backend.enums.InvoiceStatus;
import com.ella.backend.repositories.InvoiceRepository;
import com.ella.backend.services.goals.GoalAnalysisUtils;

@Component
public class DebtPayoffGoalProvider implements GoalProvider {

    private final InvoiceRepository invoiceRepository;

    public DebtPayoffGoalProvider(InvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    @Override
    public GoalDataSource getDataSource() {
        return GoalDataSource.CASHFLOW_COMBINED;
    }

    @Override
    public List<Goal> generateGoals(Person person, List<FinancialTransaction> recentTransactions) {
        if (person == null) {
            return List.of();
        }

        List<Invoice> invoices = invoiceRepository.findByCardOwner(person);
        if (invoices == null || invoices.isEmpty()) {
            return List.of();
        }

        BigDecimal outstanding = invoices.stream()
                .filter(Objects::nonNull)
                .filter(i -> i.getStatus() == InvoiceStatus.OPEN)
                .map(i -> {
                    BigDecimal total = i.getTotalAmount() != null ? i.getTotalAmount() : BigDecimal.ZERO;
                    BigDecimal paid = i.getPaidAmount() != null ? i.getPaidAmount() : BigDecimal.ZERO;
                    return total.subtract(paid);
                })
                .filter(v -> v.compareTo(BigDecimal.ZERO) > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (outstanding.compareTo(BigDecimal.valueOf(500)) < 0) {
            return List.of();
        }

        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expenses = BigDecimal.ZERO;
        if (recentTransactions != null) {
            for (FinancialTransaction tx : recentTransactions) {
                if (GoalAnalysisUtils.isIncome(tx)) {
                    income = income.add(GoalAnalysisUtils.safeAmount(tx));
                } else if (GoalAnalysisUtils.isExpense(tx)) {
                    expenses = expenses.add(GoalAnalysisUtils.safeAmount(tx));
                }
            }
        }

        int monthsCount = Math.max(3, Math.min(6, GoalAnalysisUtils.distinctMonths(recentTransactions).size()));
        BigDecimal avgIncome = GoalAnalysisUtils.averagePerMonth(income, monthsCount);
        BigDecimal avgExpenses = GoalAnalysisUtils.averagePerMonth(expenses, monthsCount);
        BigDecimal surplus = avgIncome.subtract(avgExpenses);

        BigDecimal basePayment = outstanding.divide(BigDecimal.valueOf(6), 2, RoundingMode.HALF_UP);
        BigDecimal suggested;
        if (surplus.compareTo(BigDecimal.valueOf(0)) > 0) {
            suggested = basePayment.min(surplus.multiply(BigDecimal.valueOf(0.50))).max(BigDecimal.valueOf(150));
        } else {
            suggested = basePayment.max(BigDecimal.valueOf(150));
        }

        int monthsToPay = suggested.compareTo(BigDecimal.ZERO) > 0
                ? (int) Math.ceil(outstanding.divide(suggested, 6, RoundingMode.HALF_UP).doubleValue())
                : 6;
        monthsToPay = Math.max(3, Math.min(24, monthsToPay));

        Goal goal = new Goal();
        goal.setOwner(person);
        goal.setStatus(GoalStatus.ACTIVE);
        goal.setCurrentAmount(BigDecimal.ZERO);
        goal.setDeadline(LocalDate.now().plusMonths(monthsToPay));

        goal.setTitle("Sair do vermelho");
        goal.setDescription(String.format(
                "Você tem aproximadamente %s em faturas em aberto. Tente pagar %s por mês para quitar em ~%d meses.",
                GoalAnalysisUtils.formatCurrency(outstanding),
                GoalAnalysisUtils.formatCurrency(suggested),
                monthsToPay
        ));
        goal.setTargetAmount(outstanding);

        return List.of(goal);
    }

    @Override
    public int getPriority() {
        return 2;
    }
}
