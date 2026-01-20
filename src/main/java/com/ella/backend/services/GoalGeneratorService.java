package com.ella.backend.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.ella.backend.entities.CreditCard;
import com.ella.backend.entities.FinancialTransaction;
import com.ella.backend.entities.Goal;
import com.ella.backend.entities.Person;
import com.ella.backend.enums.GoalStatus;
import com.ella.backend.enums.TransactionType;
import com.ella.backend.repositories.CreditCardRepository;
import com.ella.backend.repositories.FinancialTransactionRepository;
import com.ella.backend.repositories.GoalRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GoalGeneratorService {

    private static final int DEFAULT_SCALE = 2;

    private final GoalRepository goalRepository;
    private final FinancialTransactionRepository financialTransactionRepository;
    private final CreditCardRepository creditCardRepository;

    /**
     * Gera metas automáticas para o usuário baseado em padrões determinísticos.
     *
     * Regras:
     * - Até 4 metas
     * - Deadline: 1 mês
     * - Status: ACTIVE
     */
    public List<Goal> generateAutomaticGoals(Person person, int monthsToAnalyze) {
        int safeMonths = Math.max(1, monthsToAnalyze);

        YearMonth currentMonth = YearMonth.from(LocalDate.now());
        YearMonth startMonth = currentMonth.minusMonths(safeMonths - 1L);

        LocalDate startDate = startMonth.atDay(1);
        LocalDate endDate = currentMonth.atEndOfMonth();

        List<FinancialTransaction> recentTransactions = financialTransactionRepository
                .findByPersonAndTransactionDateBetween(person, startDate, endDate);

        if (recentTransactions == null || recentTransactions.isEmpty()) {
            return List.of();
        }

        Stats stats = calculateStats(recentTransactions, startMonth, safeMonths);

        List<Goal> generatedGoals = new ArrayList<>(4);

        // 1) Redução de Gastos Gerais (10% de redução)
        if (stats.averageMonthlyExpenses.compareTo(BigDecimal.ZERO) > 0) {
            generatedGoals.add(createGeneralReductionGoal(person, stats.averageMonthlyExpenses));
        }

        // 2) Redução de Categoria Específica (Maior gasto) (15% de redução)
        if (stats.topCategoryAmount.compareTo(BigDecimal.ZERO) > 0) {
            generatedGoals.add(createCategoryReductionGoal(person, stats.topCategory, stats.topCategoryAmount));
        }

        // 3) Manter Crédito Saudável (< 30% utilização)
        BigDecimal creditLimit = calculateCreditLimit(person);
        if (creditLimit.compareTo(BigDecimal.ZERO) > 0) {
            generatedGoals.add(createHealthyCreditGoal(person, creditLimit));
        }

        // 4) Poupar Mensalmente (10% da renda)
        if (stats.averageMonthlyIncome.compareTo(BigDecimal.ZERO) > 0) {
            generatedGoals.add(createSavingsGoal(person, stats.averageMonthlyIncome));
        }

        // Persistir (máximo 4) e retornar
        if (generatedGoals.size() > 4) {
            generatedGoals = generatedGoals.subList(0, 4);
        }

        return goalRepository.saveAll(generatedGoals);
    }

    private Stats calculateStats(List<FinancialTransaction> transactions, YearMonth startMonth, int monthsToAnalyze) {
        List<YearMonth> months = new ArrayList<>(monthsToAnalyze);
        for (int i = 0; i < monthsToAnalyze; i++) {
            months.add(startMonth.plusMonths(i));
        }

        BigDecimal totalExpenses = BigDecimal.ZERO;
        BigDecimal totalIncome = BigDecimal.ZERO;

        Map<String, BigDecimal> categoryTotals = new HashMap<>();

        for (FinancialTransaction tx : transactions) {
            if (tx == null || tx.getAmount() == null || tx.getType() == null || tx.getTransactionDate() == null) {
                continue;
            }

            YearMonth ym = YearMonth.from(tx.getTransactionDate());
            if (!months.contains(ym)) {
                continue;
            }

            if (tx.getType() == TransactionType.EXPENSE) {
                totalExpenses = totalExpenses.add(tx.getAmount());

                String category = normalizeCategory(tx.getCategory());
                categoryTotals.put(category, categoryTotals.getOrDefault(category, BigDecimal.ZERO).add(tx.getAmount()));
            } else if (tx.getType() == TransactionType.INCOME) {
                totalIncome = totalIncome.add(tx.getAmount());
            }
        }

        BigDecimal avgExpenses = safeDivide(totalExpenses, BigDecimal.valueOf(monthsToAnalyze));
        BigDecimal avgIncome = safeDivide(totalIncome, BigDecimal.valueOf(monthsToAnalyze));

        String topCategory = findTopCategory(categoryTotals);
        BigDecimal topCategoryAmount = categoryTotals.getOrDefault(topCategory, BigDecimal.ZERO);

        return new Stats(avgExpenses, avgIncome, topCategory, topCategoryAmount);
    }

    private BigDecimal calculateCreditLimit(Person person) {
        List<CreditCard> cards = creditCardRepository.findByOwner(person);
        if (cards == null || cards.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal sum = BigDecimal.ZERO;
        for (CreditCard card : cards) {
            if (card != null && card.getLimitAmount() != null) {
                sum = sum.add(card.getLimitAmount());
            }
        }
        return sum;
    }

    private Goal createGeneralReductionGoal(Person person, BigDecimal averageMonthlyExpenses) {
        BigDecimal targetAmount = averageMonthlyExpenses
                .multiply(BigDecimal.valueOf(0.9))
                .setScale(DEFAULT_SCALE, RoundingMode.HALF_UP);

        Goal goal = new Goal();
        goal.setTitle("Reduzir gastos em 10%");
        goal.setDescription(String.format(
                "Reduzir gastos de %s para %s",
                formatCurrency(averageMonthlyExpenses),
                formatCurrency(targetAmount)
        ));
        goal.setTargetAmount(targetAmount);
        goal.setCurrentAmount(BigDecimal.ZERO);
        goal.setDeadline(LocalDate.now().plusMonths(1));
        goal.setStatus(GoalStatus.ACTIVE);
        goal.setOwner(person);

        return goal;
    }

    private Goal createCategoryReductionGoal(Person person, String topCategory, BigDecimal topAmount) {
        BigDecimal targetAmount = topAmount
                .multiply(BigDecimal.valueOf(0.85))
                .setScale(DEFAULT_SCALE, RoundingMode.HALF_UP);

        String category = (topCategory == null || topCategory.isBlank()) ? "Geral" : topCategory;

        Goal goal = new Goal();
        goal.setTitle(String.format("Reduzir gastos em %s", category));
        goal.setDescription(String.format(
                "Reduzir gastos em %s de %s para %s",
                category,
                formatCurrency(topAmount),
                formatCurrency(targetAmount)
        ));
        goal.setTargetAmount(targetAmount);
        goal.setCurrentAmount(BigDecimal.ZERO);
        goal.setDeadline(LocalDate.now().plusMonths(1));
        goal.setStatus(GoalStatus.ACTIVE);
        goal.setOwner(person);

        return goal;
    }

    private Goal createHealthyCreditGoal(Person person, BigDecimal creditLimit) {
        BigDecimal maxAllowedExpenses = creditLimit
                .multiply(BigDecimal.valueOf(0.30))
                .setScale(DEFAULT_SCALE, RoundingMode.HALF_UP);

        Goal goal = new Goal();
        goal.setTitle("Manter crédito saudável");
        goal.setDescription(String.format(
                "Manter utilização de crédito abaixo de 30%% (máximo %s de %s)",
                formatCurrency(maxAllowedExpenses),
                formatCurrency(creditLimit)
        ));
        goal.setTargetAmount(maxAllowedExpenses);
        goal.setCurrentAmount(BigDecimal.ZERO);
        goal.setDeadline(LocalDate.now().plusMonths(1));
        goal.setStatus(GoalStatus.ACTIVE);
        goal.setOwner(person);

        return goal;
    }

    private Goal createSavingsGoal(Person person, BigDecimal monthlyIncome) {
        BigDecimal savingsTarget = monthlyIncome
                .multiply(BigDecimal.valueOf(0.10))
                .setScale(DEFAULT_SCALE, RoundingMode.HALF_UP);

        Goal goal = new Goal();
        goal.setTitle("Poupar 10% da renda");
        goal.setDescription(String.format(
                "Poupar %s por mês (10%% de %s)",
                formatCurrency(savingsTarget),
                formatCurrency(monthlyIncome)
        ));
        goal.setTargetAmount(savingsTarget);
        goal.setCurrentAmount(BigDecimal.ZERO);
        goal.setDeadline(LocalDate.now().plusMonths(1));
        goal.setStatus(GoalStatus.ACTIVE);
        goal.setOwner(person);

        return goal;
    }

    private static String normalizeCategory(String category) {
        if (category == null) {
            return "Outros";
        }
        String trimmed = category.trim();
        return trimmed.isEmpty() ? "Outros" : trimmed;
    }

    private static String findTopCategory(Map<String, BigDecimal> categoryTotals) {
        if (categoryTotals == null || categoryTotals.isEmpty()) {
            return "Geral";
        }
        return categoryTotals.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("Geral");
    }

    private static BigDecimal safeDivide(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return numerator.divide(denominator, DEFAULT_SCALE, RoundingMode.HALF_UP);
    }

    private static String formatCurrency(BigDecimal amount) {
        BigDecimal safe = amount == null ? BigDecimal.ZERO : amount;
        NumberFormat nf = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
        return nf.format(safe);
    }

    private record Stats(
            BigDecimal averageMonthlyExpenses,
            BigDecimal averageMonthlyIncome,
            String topCategory,
            BigDecimal topCategoryAmount
    ) {
        private Stats {
            averageMonthlyExpenses = averageMonthlyExpenses != null ? averageMonthlyExpenses : BigDecimal.ZERO;
            averageMonthlyIncome = averageMonthlyIncome != null ? averageMonthlyIncome : BigDecimal.ZERO;
            topCategory = topCategory != null ? topCategory : "Geral";
            topCategoryAmount = topCategoryAmount != null ? topCategoryAmount : BigDecimal.ZERO;
        }
    }
}
