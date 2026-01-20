package com.ella.backend.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.ella.backend.dto.dashboard.InsightDTO;
import com.ella.backend.entities.CreditCard;
import com.ella.backend.entities.FinancialTransaction;
import com.ella.backend.entities.Goal;
import com.ella.backend.entities.Person;
import com.ella.backend.enums.GoalStatus;
import com.ella.backend.enums.TransactionType;
import com.ella.backend.exceptions.ResourceNotFoundException;
import com.ella.backend.repositories.CreditCardRepository;
import com.ella.backend.repositories.FinancialTransactionRepository;
import com.ella.backend.repositories.GoalRepository;
import com.ella.backend.repositories.PersonRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DashboardInsightsService {

    private static final Locale LOCALE_PT_BR = new Locale("pt", "BR");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final PersonRepository personRepository;
    private final FinancialTransactionRepository financialTransactionRepository;
    private final CreditCardRepository creditCardRepository;
    private final GoalRepository goalRepository;

    public List<InsightDTO> getInsights(String personId, int year, int month) {
        List<InsightDTO> insights = new ArrayList<>();

    Person person = resolvePerson(personId);
    List<FinancialTransaction> currentMonthTx = getTransactionsForMonth(person, year, month);

    if (currentMonthTx.isEmpty()) {
        return insights;
    }

    // 1. Categoria com maior gasto
    addTopCategoryInsight(insights, currentMonthTx);

    // 2. Aumento de gasto (mês a mês)
    addMonthlyGrowthInsight(insights, person, year, month, currentMonthTx);

    // 3. Anomalias de gasto
    addAnomalyInsights(insights, currentMonthTx);

    // 4. Limite de crédito
    addCreditLimitInsight(insights, person, currentMonthTx);

    // 5. Orçamento excedido (heurística baseada em metas do tipo “Orçamento: <Categoria>”)
    addBudgetExceededInsights(insights, person, currentMonthTx);

        return insights;
    }

    private void addTopCategoryInsight(List<InsightDTO> insights, List<FinancialTransaction> transactions) {
    if (insights.size() >= 5) return;

    Map<String, BigDecimal> categoryTotals = transactions.stream()
        .filter(Objects::nonNull)
        .filter(t -> t.getType() == TransactionType.EXPENSE)
        .collect(Collectors.groupingBy(
            t -> normalizeCategory(t.getCategory()),
            Collectors.reducing(BigDecimal.ZERO, this::safeAmount, BigDecimal::add)
        ));

    if (categoryTotals.isEmpty()) return;

    String topCategory = categoryTotals.entrySet().stream()
        .max(Map.Entry.comparingByValue())
        .map(Map.Entry::getKey)
        .orElse("Outros");

    BigDecimal topAmount = categoryTotals.getOrDefault(topCategory, BigDecimal.ZERO);
    BigDecimal totalExpenses = categoryTotals.values().stream()
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal percentage = totalExpenses.compareTo(BigDecimal.ZERO) > 0
        ? topAmount.multiply(HUNDRED).divide(totalExpenses, 1, RoundingMode.HALF_UP)
        : BigDecimal.ZERO;

    addInsightIfRoom(insights, InsightDTO.builder()
        .type("info")
        .message(String.format(
            "Você gastou %s em %s (%.0f%% do total)",
            formatCurrency(topAmount),
            topCategory,
            percentage
        ))
        .category("Gastos")
        .build());
    }

    private void addMonthlyGrowthInsight(
        List<InsightDTO> insights,
        Person person,
        int year,
        int month,
        List<FinancialTransaction> currentMonthTx
    ) {
    if (insights.size() >= 5) return;

    BigDecimal currentMonthTotal = currentMonthTx.stream()
        .filter(Objects::nonNull)
        .filter(t -> t.getType() == TransactionType.EXPENSE)
        .map(this::safeAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    int previousMonth = month == 1 ? 12 : month - 1;
    int previousYear = month == 1 ? year - 1 : year;

    List<FinancialTransaction> previousMonthTx = getTransactionsForMonth(person, previousYear, previousMonth);
    BigDecimal previousMonthTotal = previousMonthTx.stream()
        .filter(Objects::nonNull)
        .filter(t -> t.getType() == TransactionType.EXPENSE)
        .map(this::safeAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    if (previousMonthTotal.compareTo(BigDecimal.ZERO) <= 0) return;

    BigDecimal variation = currentMonthTotal
        .subtract(previousMonthTotal)
        .divide(previousMonthTotal, 2, RoundingMode.HALF_UP)
        .multiply(HUNDRED);

    if (variation.abs().compareTo(BigDecimal.valueOf(10)) <= 0) return;

    String direction = variation.compareTo(BigDecimal.ZERO) > 0 ? "aumentaram" : "diminuíram";
    String type = variation.compareTo(BigDecimal.ZERO) > 0 ? "warning" : "success";

    addInsightIfRoom(insights, InsightDTO.builder()
        .type(type)
        .message(String.format(
            "Seus gastos %s %.0f%% em relação ao mês anterior",
            direction,
            variation.abs()
        ))
        .category("Tendência")
        .build());
    }

    private void addAnomalyInsights(List<InsightDTO> insights, List<FinancialTransaction> transactions) {
    if (insights.size() >= 5) return;

    Map<String, List<FinancialTransaction>> byCategory = transactions.stream()
        .filter(Objects::nonNull)
        .filter(t -> t.getType() == TransactionType.EXPENSE)
        .collect(Collectors.groupingBy(t -> normalizeCategory(t.getCategory())));

    Map<String, BigDecimal> categoryAverages = byCategory.entrySet().stream()
        .collect(Collectors.toMap(
            Map.Entry::getKey,
            entry -> averageAmount(entry.getValue())
        ));

    // Máximo 3 anomalias no mês, e nunca ultrapassar o total de 5 insights.
    int maxToAdd = Math.min(3, 5 - insights.size());
    if (maxToAdd <= 0) return;

    List<FinancialTransaction> anomalies = transactions.stream()
        .filter(Objects::nonNull)
        .filter(t -> t.getType() == TransactionType.EXPENSE)
        .filter(t -> {
            BigDecimal avg = categoryAverages.getOrDefault(normalizeCategory(t.getCategory()), BigDecimal.ZERO);
            if (avg.compareTo(BigDecimal.ZERO) <= 0) return false;
            return safeAmount(t).compareTo(avg.multiply(BigDecimal.valueOf(2))) > 0;
        })
        .sorted((a, b) -> safeAmount(b).compareTo(safeAmount(a)))
        .limit(maxToAdd)
        .toList();

    for (FinancialTransaction t : anomalies) {
        if (insights.size() >= 5) break;
        String category = normalizeCategory(t.getCategory());
        BigDecimal avg = categoryAverages.getOrDefault(category, BigDecimal.ZERO);

        addInsightIfRoom(insights, InsightDTO.builder()
            .type("alert")
            .message(String.format(
                "Você gastou %s em %s, muito acima do normal (média: %s)",
                formatCurrency(safeAmount(t)),
                category,
                formatCurrency(avg)
            ))
            .category("Anomalia")
            .build());
    }
    }

    private void addCreditLimitInsight(List<InsightDTO> insights, Person person, List<FinancialTransaction> transactions) {
    if (insights.size() >= 5) return;

    BigDecimal monthlyExpenses = transactions.stream()
        .filter(Objects::nonNull)
        .filter(t -> t.getType() == TransactionType.EXPENSE)
        .map(this::safeAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal creditLimit = creditCardRepository.findByOwner(person).stream()
        .map(CreditCard::getLimitAmount)
        .filter(Objects::nonNull)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    if (creditLimit.compareTo(BigDecimal.ZERO) <= 0) return;

    BigDecimal utilization = monthlyExpenses
        .divide(creditLimit, 2, RoundingMode.HALF_UP)
        .multiply(HUNDRED);

    if (utilization.compareTo(BigDecimal.valueOf(80)) <= 0) return;

    addInsightIfRoom(insights, InsightDTO.builder()
        .type("warning")
        .message(String.format("Você já utilizou %.0f%% do seu limite de crédito", utilization))
        .category("Limite")
        .build());
    }

    private void addBudgetExceededInsights(List<InsightDTO> insights, Person person, List<FinancialTransaction> transactions) {
    if (insights.size() >= 5) return;

    // Heurística: metas cujo título começa com "Orçamento:" ou "Orcamento:" são tratadas como budget por categoria.
    List<Goal> goals = goalRepository.findByOwner(person);
    if (goals == null || goals.isEmpty()) return;

    Map<String, BigDecimal> expensesByCategory = transactions.stream()
        .filter(Objects::nonNull)
        .filter(t -> t.getType() == TransactionType.EXPENSE)
        .collect(Collectors.groupingBy(
            t -> normalizeCategory(t.getCategory()).toLowerCase(LOCALE_PT_BR),
            Collectors.reducing(BigDecimal.ZERO, this::safeAmount, BigDecimal::add)
        ));

    if (expensesByCategory.isEmpty()) return;

    int remaining = 5 - insights.size();
    if (remaining <= 0) return;

    goals.stream()
        .filter(Objects::nonNull)
        .filter(g -> g.getStatus() == GoalStatus.ACTIVE)
        .map(this::parseBudgetFromGoal)
        .flatMap(Optional::stream)
        .filter(b -> b.limit().compareTo(BigDecimal.ZERO) > 0)
        .limit(remaining)
        .forEach(budget -> {
            if (insights.size() >= 5) return;
            BigDecimal spent = expensesByCategory.getOrDefault(budget.category().toLowerCase(LOCALE_PT_BR), BigDecimal.ZERO);
            if (spent.compareTo(budget.limit()) <= 0) return;

            BigDecimal excess = spent.subtract(budget.limit());
            addInsightIfRoom(insights, InsightDTO.builder()
                .type("alert")
                .message(String.format(
                    "Você gastou %s em %s, mas o orçamento era %s (excedeu %s)",
                    formatCurrency(spent),
                    budget.category(),
                    formatCurrency(budget.limit()),
                    formatCurrency(excess)
                ))
                .category("Orçamento")
                .build());
        });
    }

    private Person resolvePerson(String personId) {
    UUID personUuid = UUID.fromString(personId);
    return personRepository.findById(personUuid)
        .orElseThrow(() -> new ResourceNotFoundException("Person not found"));
    }

    private List<FinancialTransaction> getTransactionsForMonth(Person person, int year, int month) {
    java.time.YearMonth ym = java.time.YearMonth.of(year, month);
    java.time.LocalDate start = ym.atDay(1);
    java.time.LocalDate end = ym.atEndOfMonth();
    return financialTransactionRepository.findByPersonAndTransactionDateBetween(person, start, end);
    }

    private BigDecimal safeAmount(FinancialTransaction t) {
    if (t == null || t.getAmount() == null) return BigDecimal.ZERO;
    return t.getAmount();
    }

    private BigDecimal averageAmount(List<FinancialTransaction> txs) {
    if (txs == null || txs.isEmpty()) return BigDecimal.ZERO;
    BigDecimal sum = txs.stream()
        .filter(Objects::nonNull)
        .map(this::safeAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    return sum.divide(BigDecimal.valueOf(txs.size()), 2, RoundingMode.HALF_UP);
    }

    private String normalizeCategory(String category) {
    if (category == null || category.isBlank()) return "Outros";
    return category.trim();
    }

    private void addInsightIfRoom(List<InsightDTO> insights, InsightDTO insight) {
    if (insights.size() >= 5) return;
    insights.add(insight);
    }

    private String formatCurrency(BigDecimal amount) {
    NumberFormat nf = NumberFormat.getCurrencyInstance(LOCALE_PT_BR);
    BigDecimal safe = amount != null ? amount : BigDecimal.ZERO;
    return nf.format(safe);
    }

    private Optional<BudgetGoal> parseBudgetFromGoal(Goal goal) {
    if (goal == null || goal.getTitle() == null) return Optional.empty();
    String title = goal.getTitle().trim();

    String prefix1 = "orçamento:";
    String prefix2 = "orcamento:";

    String lower = title.toLowerCase(LOCALE_PT_BR);
    String category;
    if (lower.startsWith(prefix1)) {
        category = title.substring(prefix1.length()).trim();
    } else if (lower.startsWith(prefix2)) {
        category = title.substring(prefix2.length()).trim();
    } else {
        return Optional.empty();
    }

    if (category.isBlank()) return Optional.empty();

    BigDecimal limit = goal.getTargetAmount() != null ? goal.getTargetAmount() : BigDecimal.ZERO;
    return Optional.of(new BudgetGoal(category, limit));
    }

    private record BudgetGoal(String category, BigDecimal limit) {
    }
}
