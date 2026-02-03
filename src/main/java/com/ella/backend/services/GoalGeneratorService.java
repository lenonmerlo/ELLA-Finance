package com.ella.backend.services;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.ella.backend.entities.FinancialTransaction;
import com.ella.backend.entities.Goal;
import com.ella.backend.entities.Person;
import com.ella.backend.enums.GoalStatus;
import com.ella.backend.exceptions.ResourceNotFoundException;
import com.ella.backend.repositories.FinancialTransactionRepository;
import com.ella.backend.repositories.GoalRepository;
import com.ella.backend.repositories.PersonRepository;
import com.ella.backend.services.cashflow.CashflowTransactionsService;
import com.ella.backend.services.goals.providers.GoalProvider;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GoalGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(GoalGeneratorService.class);

    private static final Locale LOCALE_PT_BR = Locale.of("pt", "BR");
    private static final int MAX_ACTIVE_GOALS = 4;
    private static final int DEFAULT_MONTHS_TO_ANALYZE = 6;

    private final List<GoalProvider> goalProviders;
    private final PersonRepository personRepository;
    private final GoalRepository goalRepository;
    private final FinancialTransactionRepository financialTransactionRepository;
    private final CashflowTransactionsService cashflowTransactionsService;

    /**
     * Compatibilidade: usado por {@link DashboardGoalsService}.
     *
     * A lógica foi migrada para providers (Strategy) e o método agora só orquestra.
     */
    public List<Goal> generateAutomaticGoals(Person person, int monthsToAnalyze) {
        if (person == null || person.getId() == null) {
            return List.of();
        }
        int months = monthsToAnalyze > 0 ? monthsToAnalyze : DEFAULT_MONTHS_TO_ANALYZE;
        return generateGoalsForPerson(person, Math.max(3, Math.min(6, months)));
    }

    /**
     * Gera metas automáticas para o usuário (até completar 4 metas ACTIVE).
     */
    public List<Goal> generateGoalsForUser(String personId) {
        Person person = personRepository.findById(UUID.fromString(personId))
                .orElseThrow(() -> new ResourceNotFoundException("Person not found"));
        return generateGoalsForPerson(person, DEFAULT_MONTHS_TO_ANALYZE);
    }

    private List<Goal> generateGoalsForPerson(Person person, int monthsToAnalyze) {
        long activeGoals = goalRepository.countByOwnerAndStatus(person, GoalStatus.ACTIVE);
        if (activeGoals >= MAX_ACTIVE_GOALS) {
            return List.of();
        }

        int limitToGenerate = (int) Math.max(0, MAX_ACTIVE_GOALS - activeGoals);
        if (limitToGenerate == 0) {
            return List.of();
        }

        List<Goal> existing = Optional.ofNullable(goalRepository.findByOwner(person)).orElse(List.of());
        Set<String> existingActiveTitles = existing.stream()
                .filter(g -> g != null && g.getStatus() == GoalStatus.ACTIVE)
                .map(Goal::getTitle)
                .filter(t -> t != null && !t.isBlank())
                .map(t -> t.trim().toLowerCase(LOCALE_PT_BR))
                .collect(Collectors.toSet());

        Set<String> newlyAddedTitles = new java.util.HashSet<>();

        List<FinancialTransaction> recentTransactions = fetchRecentTransactions(person, monthsToAnalyze);

        boolean needsCashflow = goalProviders.stream()
            .anyMatch(p -> p != null && p.getDataSource() == GoalProvider.GoalDataSource.CASHFLOW_COMBINED);

        List<FinancialTransaction> cashflowTransactions = needsCashflow
            ? fetchRecentCashflowTransactions(person, monthsToAnalyze)
            : List.of();

        List<Goal> newGoals = goalProviders.stream()
                .sorted(java.util.Comparator.comparingInt(GoalProvider::getPriority))
                .flatMap(provider -> {
                    try {
                        List<FinancialTransaction> txs = provider.getDataSource() == GoalProvider.GoalDataSource.CASHFLOW_COMBINED
                                ? cashflowTransactions
                                : recentTransactions;
                        if (txs == null) {
                            txs = List.of();
                        }
                        return provider.generateGoals(person, txs).stream();
                    } catch (Exception e) {
                        log.warn("Goal provider {} failed for personId={}", provider.getClass().getSimpleName(), person.getId(), e);
                        return java.util.stream.Stream.empty();
                    }
                })
                .filter(g -> g != null && g.getTitle() != null && !g.getTitle().isBlank())
                .filter(g -> {
                    String key = g.getTitle().trim().toLowerCase(LOCALE_PT_BR);
                    if (existingActiveTitles.contains(key)) {
                        return false;
                    }
                    return newlyAddedTitles.add(key);
                })
                .limit(limitToGenerate)
                .toList();

        if (newGoals.isEmpty()) {
            return List.of();
        }

        return goalRepository.saveAll(newGoals);
    }

    private List<FinancialTransaction> fetchRecentTransactions(Person person, int monthsToAnalyze) {
        int months = Math.max(3, Math.min(6, monthsToAnalyze));
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusMonths(months).withDayOfMonth(1);
        return Optional.ofNullable(financialTransactionRepository.findByPersonAndTransactionDateBetween(person, start, end))
                .orElse(List.of());
    }

    private List<FinancialTransaction> fetchRecentCashflowTransactions(Person person, int monthsToAnalyze) {
        int months = Math.max(3, Math.min(6, monthsToAnalyze));
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusMonths(months).withDayOfMonth(1);
        return Optional.ofNullable(cashflowTransactionsService.fetchCashflowTransactions(person, start, end))
                .orElse(List.of());
    }
}
