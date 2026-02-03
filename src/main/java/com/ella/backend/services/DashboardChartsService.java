package com.ella.backend.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.ella.backend.dto.dashboard.CategoryBreakdownDTO;
import com.ella.backend.dto.dashboard.ChartsDTO;
import com.ella.backend.dto.dashboard.MonthlyEvolutionDTO;
import com.ella.backend.dto.dashboard.MonthlyPointDTO;
import com.ella.backend.entities.BankStatementTransaction;
import com.ella.backend.entities.FinancialTransaction;
import com.ella.backend.entities.Person;
import com.ella.backend.enums.TransactionType;
import com.ella.backend.exceptions.ResourceNotFoundException;
import com.ella.backend.repositories.BankStatementTransactionRepository;
import com.ella.backend.repositories.FinancialTransactionRepository;
import com.ella.backend.repositories.PersonRepository;
import com.ella.backend.services.cashflow.BankStatementCashflowHeuristics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardChartsService {

    private final PersonRepository personRepository;
    private final FinancialTransactionRepository financialTransactionRepository;
        private final BankStatementTransactionRepository bankStatementTransactionRepository;

        public ChartsDTO getCharts(String personId, int year, Integer month) {
        UUID personUuid = UUID.fromString(personId);
        Person person = personRepository.findById(personUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Person not found"));

                YearMonth target = month != null && month >= 1 && month <= 12 ? YearMonth.of(year, month) : null;
                LocalDate rangeStart;
                LocalDate rangeEnd;

                if (target != null) {
                        YearMonth windowStartYm = target.minusMonths(5);
                        rangeStart = windowStartYm.atDay(1);
                        rangeEnd = target.atEndOfMonth();
                } else {
                        rangeStart = YearMonth.of(year, 1).atDay(1);
                        rangeEnd = YearMonth.of(year, 12).atEndOfMonth();
                }

                log.info("[DashboardChartsService] personId={} year={} month={} range {} -> {}", personId, year, month, rangeStart, rangeEnd);

                List<FinancialTransaction> txs = financialTransactionRepository.findByPersonAndTransactionDateBetween(
                                person, rangeStart, rangeEnd
                );

                List<BankStatementTransaction> statementTxs = bankStatementTransactionRepository
                        .findForUserAndPeriod(person.getId(), rangeStart, rangeEnd)
                        .stream()
                        .filter(t -> !BankStatementCashflowHeuristics.shouldIgnore(t))
                        .toList();

        if (log.isInfoEnabled()) {
                        List<String> sample = txs.stream()
                    .limit(5)
                    .map(tx -> String.format("%s|%s|%s", tx.getTransactionDate(), tx.getType(), tx.getAmount()))
                    .toList();
                        log.info("[DashboardChartsService] loaded {} txs for charts; samples={}", txs.size(), sample);
        }

                MonthlyEvolutionDTO monthlyEvolution = buildMonthlyEvolution(txs, statementTxs, year, target);
                List<CategoryBreakdownDTO> categoryBreakdown = buildCategoryBreakdown(txs, statementTxs, target);

        return ChartsDTO.builder()
                .monthlyEvolution(monthlyEvolution)
                .categoryBreakdown(categoryBreakdown)
                .build();
    }

        private MonthlyEvolutionDTO buildMonthlyEvolution(List<FinancialTransaction> txs, List<BankStatementTransaction> statementTxs, int year, YearMonth target) {
                Map<YearMonth, List<FinancialTransaction>> grouped = txs.stream()
                .filter(tx -> tx.getTransactionDate() != null)
                .collect(Collectors.groupingBy(
                        tx -> YearMonth.from(tx.getTransactionDate())
                ));

                Map<YearMonth, List<BankStatementTransaction>> groupedStatements = statementTxs.stream()
                        .filter(tx -> tx != null && tx.getTransactionDate() != null)
                        .collect(Collectors.groupingBy(tx -> YearMonth.from(tx.getTransactionDate())));

        List<MonthlyPointDTO> points = new ArrayList<>();
                if (target != null) {
                        for (int i = 5; i >= 0; i--) {
                                YearMonth ym = target.minusMonths(i);
                                List<FinancialTransaction> monthTx = grouped.getOrDefault(ym, Collections.emptyList());
                                List<BankStatementTransaction> monthStatements = groupedStatements.getOrDefault(ym, Collections.emptyList());

                                BigDecimal incomeFinancial = sumByType(monthTx, TransactionType.INCOME);
                                BigDecimal incomeChecking = sumStatementByType(monthStatements, BankStatementTransaction.Type.CREDIT);
                                BigDecimal income = incomeFinancial.add(incomeChecking);

                                BigDecimal expensesCard = sumCardExpenses(monthTx);
                                BigDecimal expensesOther = sumNonCardExpenses(monthTx);
                                BigDecimal expensesChecking = sumStatementByType(monthStatements, BankStatementTransaction.Type.DEBIT).add(expensesOther);
                                BigDecimal expenses = expensesCard.add(expensesChecking);

                                String label = ym.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
                                points.add(MonthlyPointDTO.builder()
                                                .monthLabel(label)
                                                .income(income)
                                                .expenses(expenses)
                                                .incomeChecking(incomeChecking)
                                                .expensesChecking(expensesChecking)
                                                .expensesCard(expensesCard)
                                                .build());
                        }
                } else {
                        for (int m = 1; m <= 12; m++) {
                                YearMonth ym = YearMonth.of(year, m);
                                List<FinancialTransaction> monthTx = grouped.getOrDefault(ym, Collections.emptyList());

                                List<BankStatementTransaction> monthStatements = groupedStatements.getOrDefault(ym, Collections.emptyList());

                                BigDecimal incomeFinancial = sumByType(monthTx, TransactionType.INCOME);
                                BigDecimal incomeChecking = sumStatementByType(monthStatements, BankStatementTransaction.Type.CREDIT);
                                BigDecimal income = incomeFinancial.add(incomeChecking);

                                BigDecimal expensesCard = sumCardExpenses(monthTx);
                                BigDecimal expensesOther = sumNonCardExpenses(monthTx);
                                BigDecimal expensesChecking = sumStatementByType(monthStatements, BankStatementTransaction.Type.DEBIT).add(expensesOther);
                                BigDecimal expenses = expensesCard.add(expensesChecking);

                                String label = String.format("%04d-%02d", year, m);

                                points.add(MonthlyPointDTO.builder()
                                                .monthLabel(label)
                                                .income(income)
                                                .expenses(expenses)
                                                .incomeChecking(incomeChecking)
                                                .expensesChecking(expensesChecking)
                                                .expensesCard(expensesCard)
                                                .build());
                        }
                }

        return MonthlyEvolutionDTO.builder()
                .points(points)
                .build();
    }

        private List<CategoryBreakdownDTO> buildCategoryBreakdown(List<FinancialTransaction> txs, List<BankStatementTransaction> statementTxs, YearMonth targetMonth) {
                Map<String, BigDecimal> byCategory = new java.util.HashMap<>();

                for (FinancialTransaction tx : txs) {
                        if (tx == null || tx.getType() != TransactionType.EXPENSE || tx.getAmount() == null) continue;
                        if (targetMonth != null && tx.getTransactionDate() != null && !YearMonth.from(tx.getTransactionDate()).equals(targetMonth)) {
                                continue;
                        }
                        if (targetMonth != null && tx.getTransactionDate() == null) {
                                continue;
                        }
                        String category = sanitizeCategory(tx.getCategory());
                        byCategory.merge(category, tx.getAmount().abs(), BigDecimal::add);
                }

                for (BankStatementTransaction stx : statementTxs) {
                        if (stx == null || stx.getAmount() == null || stx.getType() != BankStatementTransaction.Type.DEBIT) continue;
                        if (targetMonth != null && stx.getTransactionDate() != null && !YearMonth.from(stx.getTransactionDate()).equals(targetMonth)) {
                                continue;
                        }
                        if (targetMonth != null && stx.getTransactionDate() == null) {
                                continue;
                        }
                        String category = sanitizeCategory(BankStatementCashflowHeuristics.categorize(stx.getDescription(), stx.getType()));
                        byCategory.merge(category, stx.getAmount().abs(), BigDecimal::add);
                }

                if (byCategory.isEmpty()) {
                        return Collections.emptyList();
                }

                BigDecimal totalExpenses = byCategory.values().stream()
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                if (totalExpenses.compareTo(BigDecimal.ZERO) == 0) {
                        return Collections.emptyList();
                }

                return byCategory.entrySet().stream()
                        .map(entry -> {
                                BigDecimal total = entry.getValue();
                                BigDecimal percentage = total
                                        .multiply(BigDecimal.valueOf(100))
                                        .divide(totalExpenses, 2, RoundingMode.HALF_UP);

                                return CategoryBreakdownDTO.builder()
                                        .category(entry.getKey())
                                        .total(total)
                                        .percentage(percentage)
                                        .build();
                        })
                        .sorted(Comparator.comparing(CategoryBreakdownDTO::getTotal).reversed())
                        .toList();
        }

        private String sanitizeCategory(String category) {
                if (category == null || category.isBlank()) {
                        return "Outros";
                }
                return category.trim();
        }

    private BigDecimal sumByType(List<FinancialTransaction> txs, TransactionType type) {
        return txs.stream()
                .filter(tx -> tx.getType() == type)
                .map(FinancialTransaction::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

        private BigDecimal sumStatementByType(List<BankStatementTransaction> txs, BankStatementTransaction.Type type) {
                return txs.stream()
                        .filter(tx -> tx != null && tx.getType() == type)
                        .map(BankStatementTransaction::getAmount)
                        .filter(Objects::nonNull)
                        .map(BigDecimal::abs)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        private BigDecimal sumCardExpenses(List<FinancialTransaction> txs) {
                return txs.stream()
                        .filter(tx -> tx != null && tx.getType() == TransactionType.EXPENSE)
                        .filter(tx -> tx.getPurchaseDate() != null)
                        .map(FinancialTransaction::getAmount)
                        .filter(Objects::nonNull)
                        .map(BigDecimal::abs)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        private BigDecimal sumNonCardExpenses(List<FinancialTransaction> txs) {
                return txs.stream()
                        .filter(tx -> tx != null && tx.getType() == TransactionType.EXPENSE)
                        .filter(tx -> tx.getPurchaseDate() == null)
                        .map(FinancialTransaction::getAmount)
                        .filter(Objects::nonNull)
                        .map(BigDecimal::abs)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
}
