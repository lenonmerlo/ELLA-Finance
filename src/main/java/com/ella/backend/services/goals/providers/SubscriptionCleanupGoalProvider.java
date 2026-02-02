package com.ella.backend.services.goals.providers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.ella.backend.entities.FinancialTransaction;
import com.ella.backend.entities.Goal;
import com.ella.backend.entities.Person;
import com.ella.backend.enums.GoalStatus;
import com.ella.backend.services.goals.GoalAnalysisUtils;

@Component
public class SubscriptionCleanupGoalProvider implements GoalProvider {

    private static final Locale LOCALE_PT_BR = Locale.of("pt", "BR");

    @Override
    public List<Goal> generateGoals(Person person, List<FinancialTransaction> recentTransactions) {
        if (person == null || recentTransactions == null || recentTransactions.isEmpty()) {
            return List.of();
        }

        YearMonth current = YearMonth.from(LocalDate.now());
        List<YearMonth> required = GoalAnalysisUtils.trailingMonthsInclusive(current, 3);
        Set<YearMonth> requiredSet = Set.copyOf(required);

        List<FinancialTransaction> candidates = recentTransactions.stream()
                .filter(Objects::nonNull)
                .filter(GoalAnalysisUtils::isExpense)
                .filter(tx -> GoalAnalysisUtils.safeAmount(tx).compareTo(BigDecimal.valueOf(8)) >= 0)
                .filter(this::isLikelySubscription)
                .toList();

        if (candidates.isEmpty()) {
            return List.of();
        }

        Map<String, Map<YearMonth, BigDecimal>> monthlyByKey = new HashMap<>();
        for (FinancialTransaction tx : candidates) {
            if (tx.getTransactionDate() == null) {
                continue;
            }
            YearMonth ym = YearMonth.from(tx.getTransactionDate());
            if (!requiredSet.contains(ym)) {
                continue;
            }
            String key = GoalAnalysisUtils.normalizeDescriptionKey(tx.getDescription());
            if (key.isBlank()) {
                continue;
            }
            monthlyByKey.computeIfAbsent(key, k -> new HashMap<>());
            Map<YearMonth, BigDecimal> perMonth = monthlyByKey.get(key);
            perMonth.put(ym, perMonth.getOrDefault(ym, BigDecimal.ZERO).add(GoalAnalysisUtils.safeAmount(tx)));
        }

        Map<String, BigDecimal> estimatedMonthly = GoalAnalysisUtils.estimatedMonthlyCostBySubscriptionKey(monthlyByKey, required);
        if (estimatedMonthly.isEmpty()) {
            return List.of();
        }

        int count = estimatedMonthly.size();
        if (count < 3) {
            return List.of();
        }

        BigDecimal totalMonthly = estimatedMonthly.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalMonthly.compareTo(BigDecimal.valueOf(30)) < 0) {
            return List.of();
        }

        List<String> topKeys = GoalAnalysisUtils.topKeysByAmount(estimatedMonthly, 4);
        String listText = topKeys.stream()
                .map(k -> String.format("%s (%s)", k, GoalAnalysisUtils.formatCurrency(estimatedMonthly.get(k))))
                .collect(Collectors.joining(", "));

        BigDecimal annual = totalMonthly.multiply(BigDecimal.valueOf(12));

        Goal goal = new Goal();
        goal.setOwner(person);
        goal.setStatus(GoalStatus.ACTIVE);
        goal.setCurrentAmount(BigDecimal.ZERO);
        goal.setDeadline(LocalDate.now().plusDays(30));
        goal.setTitle("Auditar e limpar assinaturas");
        goal.setDescription(String.format(
                "Você parece ter %d assinaturas recorrentes (últimos 3 meses). Revise e cancele as que não usa. Custo estimado: %s/mês (%s/ano). Principais: %s.",
                count,
                GoalAnalysisUtils.formatCurrency(totalMonthly),
                GoalAnalysisUtils.formatCurrency(annual),
                listText
        ));
        goal.setTargetAmount(totalMonthly);

        return List.of(goal);
    }

    private boolean isLikelySubscription(FinancialTransaction tx) {
        String category = GoalAnalysisUtils.normalizeCategory(tx.getCategory()).toLowerCase(LOCALE_PT_BR);
        String d = tx.getDescription() != null ? tx.getDescription().toLowerCase(LOCALE_PT_BR) : "";
        if (d.isBlank()) {
            return false;
        }

        String[] negative = new String[]{"pix", "transfer", "ted", "doc", "boleto", "saque", "estorno", "ajuste", "fatura", "pagamento"};
        for (String n : negative) {
            if (d.contains(n)) {
                return false;
            }
        }

        if (category.contains("assin")) {
            return true;
        }

        String[] positive = new String[]{
                "netflix", "spotify", "disney", "prime", "hbo", "apple", "google", "youtube",
                "deezer", "icloud", "microsoft", "adobe", "gym", "academia", "fitness", "plano"
        };
        for (String p : positive) {
            if (d.contains(p)) {
                return true;
            }
        }

        return d.contains("assin") || d.contains("subscription") || d.contains("stream");
    }

    @Override
    public int getPriority() {
        return 3;
    }
}
