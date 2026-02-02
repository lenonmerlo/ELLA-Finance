package com.ella.backend.services.insights.providers;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.ella.backend.dto.dashboard.InsightDTO;
import com.ella.backend.entities.FinancialTransaction;
import com.ella.backend.entities.Goal;
import com.ella.backend.entities.Person;
import com.ella.backend.enums.GoalStatus;
import com.ella.backend.repositories.GoalRepository;
import com.ella.backend.services.insights.InsightDataCache;
import com.ella.backend.services.insights.InsightUtils;

@Component
@Order(60)
public class BudgetExceededInsightProvider implements InsightProvider {

    private static final Locale LOCALE_PT_BR = Locale.of("pt", "BR");

    private final InsightDataCache insightDataCache;
    private final GoalRepository goalRepository;

    public BudgetExceededInsightProvider(InsightDataCache insightDataCache, GoalRepository goalRepository) {
        this.insightDataCache = insightDataCache;
        this.goalRepository = goalRepository;
    }

    @Override
    public List<InsightDTO> generate(Person person, int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        List<FinancialTransaction> txs = insightDataCache.getTransactionsForMonth(person, ym);
        if (txs == null || txs.isEmpty()) {
            return List.of();
        }

        // Heurística: metas cujo título começa com "Orçamento:" ou "Orcamento:" são tratadas como budget por categoria.
        List<Goal> goals = goalRepository.findByOwner(person);
        if (goals == null || goals.isEmpty()) {
            return List.of();
        }

        Map<String, BigDecimal> expensesByCategory = txs.stream()
                .filter(Objects::nonNull)
                .filter(InsightUtils::isExpense)
                .collect(Collectors.groupingBy(
                        t -> InsightUtils.normalizeCategory(t.getCategory()).toLowerCase(LOCALE_PT_BR),
                        Collectors.reducing(BigDecimal.ZERO, InsightUtils::safeAmount, BigDecimal::add)
                ));

        if (expensesByCategory.isEmpty()) {
            return List.of();
        }

        // 1 insight por enquanto (o service fará o limite geral)
        return goals.stream()
                .filter(Objects::nonNull)
                .filter(g -> g.getStatus() == GoalStatus.ACTIVE)
                .map(this::parseBudgetFromGoal)
                .flatMap(Optional::stream)
                .filter(b -> b.limit().compareTo(BigDecimal.ZERO) > 0)
                .map(budget -> {
                    BigDecimal spent = expensesByCategory.getOrDefault(budget.category().toLowerCase(LOCALE_PT_BR), BigDecimal.ZERO);
                    if (spent.compareTo(budget.limit()) <= 0) {
                        return null;
                    }

                    BigDecimal excess = spent.subtract(budget.limit());
                    return InsightDTO.builder()
                            .type("alert")
                            .message(String.format(
                                    "Você gastou %s em %s, mas o orçamento era %s (excedeu %s)",
                                    InsightUtils.formatCurrency(spent),
                                    budget.category(),
                                    InsightUtils.formatCurrency(budget.limit()),
                                    InsightUtils.formatCurrency(excess)
                            ))
                            .category("Orçamento")
                            .build();
                })
                .filter(Objects::nonNull)
                .limit(1)
                .toList();
    }

    private Optional<BudgetGoal> parseBudgetFromGoal(Goal goal) {
        if (goal == null || goal.getTitle() == null) {
            return Optional.empty();
        }

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

        if (category.isBlank()) {
            return Optional.empty();
        }

        BigDecimal limit = goal.getTargetAmount() != null ? goal.getTargetAmount() : BigDecimal.ZERO;
        return Optional.of(new BudgetGoal(category, limit));
    }

    private record BudgetGoal(String category, BigDecimal limit) {
    }
}
