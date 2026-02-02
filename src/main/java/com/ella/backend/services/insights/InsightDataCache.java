package com.ella.backend.services.insights;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import com.ella.backend.entities.FinancialTransaction;
import com.ella.backend.entities.Person;
import com.ella.backend.repositories.FinancialTransactionRepository;

@Component
@RequestScope
public class InsightDataCache {

    private final FinancialTransactionRepository financialTransactionRepository;
    private final Map<YearMonth, List<FinancialTransaction>> txByMonth = new HashMap<>();

    public InsightDataCache(FinancialTransactionRepository financialTransactionRepository) {
        this.financialTransactionRepository = financialTransactionRepository;
    }

    public List<FinancialTransaction> getTransactionsForMonth(Person person, int year, int month) {
        return getTransactionsForMonth(person, YearMonth.of(year, month));
    }

    public List<FinancialTransaction> getTransactionsForMonth(Person person, YearMonth yearMonth) {
        return txByMonth.computeIfAbsent(yearMonth, ym -> {
            LocalDate start = ym.atDay(1);
            LocalDate end = ym.atEndOfMonth();
            return financialTransactionRepository.findByPersonAndTransactionDateBetween(person, start, end);
        });
    }

    public static YearMonth previousMonth(YearMonth current) {
        return current.minusMonths(1);
    }

    public static List<YearMonth> previousMonths(YearMonth current, int count) {
        if (count <= 0) {
            return List.of();
        }
        return java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(current::minusMonths)
                .toList();
    }

    public static List<YearMonth> trailingMonthsInclusive(YearMonth current, int monthsInclusive) {
        if (monthsInclusive <= 0) {
            return List.of();
        }
        int previous = monthsInclusive - 1;
        List<YearMonth> prev = previousMonths(current, previous);
        java.util.List<YearMonth> out = new java.util.ArrayList<>(monthsInclusive);
        out.add(current);
        out.addAll(prev);
        // out: [current, current-1, ...] (order useful for some providers)
        return out;
    }
}
