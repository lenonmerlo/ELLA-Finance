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
import com.ella.backend.entities.FinancialTransaction;
import com.ella.backend.entities.Person;
import com.ella.backend.enums.TransactionType;
import com.ella.backend.exceptions.ResourceNotFoundException;
import com.ella.backend.repositories.FinancialTransactionRepository;
import com.ella.backend.repositories.PersonRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardChartsService {

    private final PersonRepository personRepository;
    private final FinancialTransactionRepository financialTransactionRepository;

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

        if (log.isInfoEnabled()) {
                        List<String> sample = txs.stream()
                    .limit(5)
                    .map(tx -> String.format("%s|%s|%s", tx.getTransactionDate(), tx.getType(), tx.getAmount()))
                    .toList();
                        log.info("[DashboardChartsService] loaded {} txs for charts; samples={}", txs.size(), sample);
        }

                MonthlyEvolutionDTO monthlyEvolution = buildMonthlyEvolution(txs, year, target);
                List<CategoryBreakdownDTO> categoryBreakdown = buildCategoryBreakdown(txs, target);

        return ChartsDTO.builder()
                .monthlyEvolution(monthlyEvolution)
                .categoryBreakdown(categoryBreakdown)
                .build();
    }

        private MonthlyEvolutionDTO buildMonthlyEvolution(List<FinancialTransaction> txs, int year, YearMonth target) {
                Map<YearMonth, List<FinancialTransaction>> grouped = txs.stream()
                .filter(tx -> tx.getTransactionDate() != null)
                .collect(Collectors.groupingBy(
                        tx -> YearMonth.from(tx.getTransactionDate())
                ));

        List<MonthlyPointDTO> points = new ArrayList<>();
                if (target != null) {
                        for (int i = 5; i >= 0; i--) {
                                YearMonth ym = target.minusMonths(i);
                                List<FinancialTransaction> monthTx = grouped.getOrDefault(ym, Collections.emptyList());
                                BigDecimal income = sumByType(monthTx, TransactionType.INCOME);
                                BigDecimal expenses = sumByType(monthTx, TransactionType.EXPENSE);
                                String label = ym.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
                                points.add(MonthlyPointDTO.builder()
                                                .monthLabel(label)
                                                .income(income)
                                                .expenses(expenses)
                                                .build());
                        }
                } else {
                        for (int m = 1; m <= 12; m++) {
                                YearMonth ym = YearMonth.of(year, m);
                                List<FinancialTransaction> monthTx = grouped.getOrDefault(ym, Collections.emptyList());

                                BigDecimal income = sumByType(monthTx, TransactionType.INCOME);
                                BigDecimal expenses = sumByType(monthTx, TransactionType.EXPENSE);

                                String label = String.format("%04d-%02d", year, m);

                                points.add(MonthlyPointDTO.builder()
                                                .monthLabel(label)
                                                .income(income)
                                                .expenses(expenses)
                                                .build());
                        }
                }

        return MonthlyEvolutionDTO.builder()
                .points(points)
                .build();
    }

        private List<CategoryBreakdownDTO> buildCategoryBreakdown(List<FinancialTransaction> txs, YearMonth targetMonth) {
                List<FinancialTransaction> filtered = txs.stream()
                                .filter(tx -> tx.getType() == TransactionType.EXPENSE)
                                .filter(tx -> {
                                        if (targetMonth == null || tx.getTransactionDate() == null) {
                                                return true;
                                        }
                                        YearMonth ym = YearMonth.from(tx.getTransactionDate());
                                        return ym.equals(targetMonth);
                                })
                                .toList();

                if (filtered.isEmpty()) {
                        return Collections.emptyList();
                }

                BigDecimal totalExpenses = filtered.stream()
                .map(FinancialTransaction::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalExpenses.compareTo(BigDecimal.ZERO) == 0) {
            return Collections.emptyList();
        }

                Map<String, BigDecimal> byCategory = filtered.stream()
                .collect(Collectors.groupingBy(
                                                tx -> sanitizeCategory(tx.getCategory()),
                        Collectors.reducing(BigDecimal.ZERO, FinancialTransaction::getAmount, BigDecimal::add)
                ));

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
}
