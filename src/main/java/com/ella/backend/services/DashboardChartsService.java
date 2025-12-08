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

@Service
@RequiredArgsConstructor
public class DashboardChartsService {

    private final PersonRepository personRepository;
    private final FinancialTransactionRepository financialTransactionRepository;

    public ChartsDTO getCharts(String personId, int year) {
        UUID personUuid = UUID.fromString(personId);
        Person person = personRepository.findById(personUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Person not found"));

        LocalDate yearStart = YearMonth.of(year, 1).atDay(1);
        LocalDate yearEnd = YearMonth.of(year, 12).atEndOfMonth();

        List<FinancialTransaction> yearTx = financialTransactionRepository.findByPersonAndTransactionDateBetween(
                person, yearStart, yearEnd
        );

        MonthlyEvolutionDTO monthlyEvolution = buildMonthlyEvolution(yearTx, year);
        
        // For category breakdown, usually it's for a specific month, but the request only has year?
        // The user prompt said: GET /api/dashboard/{personId}/charts?year=2025&type=monthly
        // And the response example showed category breakdown.
        // If it's for the whole year, we use yearTx. If it's for a month, we need month param.
        // I'll assume it's for the whole year if no month is provided, or I should add month param.
        // The user prompt didn't specify month in the charts request, only year.
        // So I'll calculate category breakdown for the whole year.
        List<CategoryBreakdownDTO> categoryBreakdown = buildCategoryBreakdown(yearTx);

        return ChartsDTO.builder()
                .monthlyEvolution(monthlyEvolution)
                .categoryBreakdown(categoryBreakdown)
                .build();
    }

    private MonthlyEvolutionDTO buildMonthlyEvolution(List<FinancialTransaction> yearTx, int year) {
        Map<YearMonth, List<FinancialTransaction>> grouped = yearTx.stream()
                .filter(tx -> tx.getTransactionDate() != null)
                .collect(Collectors.groupingBy(
                        tx -> YearMonth.from(tx.getTransactionDate())
                ));

        List<MonthlyPointDTO> points = new ArrayList<>();

        for (int m = 1; m <= 12; m++) {
            YearMonth ym = YearMonth.of(year, m);
            List<FinancialTransaction> txs = grouped.getOrDefault(ym, Collections.emptyList());

            BigDecimal income = sumByType(txs, TransactionType.INCOME);
            BigDecimal expenses = sumByType(txs, TransactionType.EXPENSE);

            String label = String.format("%04d-%02d", year, m);

            points.add(MonthlyPointDTO.builder()
                    .monthLabel(label)
                    .income(income)
                    .expenses(expenses)
                    .build());
        }

        return MonthlyEvolutionDTO.builder()
                .points(points)
                .build();
    }

    private List<CategoryBreakdownDTO> buildCategoryBreakdown(List<FinancialTransaction> txs) {
        List<FinancialTransaction> expensesTx = txs.stream()
                .filter(tx -> tx.getType() == TransactionType.EXPENSE)
                .toList();

        BigDecimal totalExpenses = expensesTx.stream()
                .map(FinancialTransaction::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalExpenses.compareTo(BigDecimal.ZERO) == 0) {
            return Collections.emptyList();
        }

        Map<String, BigDecimal> byCategory = expensesTx.stream()
                .collect(Collectors.groupingBy(
                        FinancialTransaction::getCategory,
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

    private BigDecimal sumByType(List<FinancialTransaction> txs, TransactionType type) {
        return txs.stream()
                .filter(tx -> tx.getType() == type)
                .map(FinancialTransaction::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
