package com.ella.backend.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.ella.backend.entities.CreditCard;
import com.ella.backend.entities.FinancialTransaction;
import com.ella.backend.entities.Invoice;
import com.ella.backend.entities.Person;
import com.ella.backend.entities.Score;
import com.ella.backend.enums.InvoiceStatus;
import com.ella.backend.enums.TransactionStatus;
import com.ella.backend.enums.TransactionType;
import com.ella.backend.exceptions.ResourceNotFoundException;
import com.ella.backend.repositories.CreditCardRepository;
import com.ella.backend.repositories.FinancialTransactionRepository;
import com.ella.backend.repositories.InvoiceRepository;
import com.ella.backend.repositories.PersonRepository;
import com.ella.backend.repositories.ScoreRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ScoreService {

    private static final double WEIGHT_CREDIT_UTILIZATION = 0.25;
    private static final double WEIGHT_ON_TIME_PAYMENT = 0.25;
    private static final double WEIGHT_SPENDING_DIVERSITY = 0.20;
    private static final double WEIGHT_SPENDING_CONSISTENCY = 0.15;
    private static final double WEIGHT_CREDIT_HISTORY = 0.15;

    private final ScoreRepository scoreRepository;
    private final InvoiceRepository invoiceRepository;
    private final FinancialTransactionRepository transactionRepository;
    private final PersonRepository personRepository;
    private final CreditCardRepository creditCardRepository;

    @Value("${ella.score.diversity.total-categories:10}")
    private int totalCategories = 10;

    @Value("${ella.score.diversity.lookback-days:90}")
    private int diversityLookbackDays = 90;

    @Value("${ella.score.consistency.months:3}")
    private int consistencyMonths = 3;

    public Score calculateScore(UUID personId) {
        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa não encontrada"));

        int creditUtilizationScore = calculateCreditUtilization(person);
        int onTimePaymentScore = calculateOnTimePayment(person);
        int spendingDiversityScore = calculateSpendingDiversity(person);
        int spendingConsistencyScore = calculateSpendingConsistency(person);
        int creditHistoryScore = calculateCreditHistory(person);

        int finalScore = clampScore((int) Math.round(
                (creditUtilizationScore * WEIGHT_CREDIT_UTILIZATION)
                        + (onTimePaymentScore * WEIGHT_ON_TIME_PAYMENT)
                        + (spendingDiversityScore * WEIGHT_SPENDING_DIVERSITY)
                        + (spendingConsistencyScore * WEIGHT_SPENDING_CONSISTENCY)
                        + (creditHistoryScore * WEIGHT_CREDIT_HISTORY)
        ));

        Score score = new Score();
        score.setPerson(person);
        score.setScoreValue(finalScore);
        score.setCalculationDate(LocalDate.now());
        score.setCreditUtilizationScore(clampScore(creditUtilizationScore));
        score.setOnTimePaymentScore(clampScore(onTimePaymentScore));
        score.setSpendingDiversityScore(clampScore(spendingDiversityScore));
        score.setSpendingConsistencyScore(clampScore(spendingConsistencyScore));
        score.setCreditHistoryScore(clampScore(creditHistoryScore));

        return scoreRepository.save(score);
    }

    private int calculateCreditUtilization(Person person) {
        List<CreditCard> cards = creditCardRepository.findByOwner(person);
        BigDecimal totalLimit = cards.stream()
                .map(CreditCard::getLimitAmount)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalLimit.compareTo(BigDecimal.ZERO) <= 0) {
            return 100;
        }

        Invoice latest = invoiceRepository.findTopByCardOwnerAndDeletedAtIsNullOrderByYearDescMonthDesc(person)
                .orElse(null);

        if (latest == null) {
            return 100;
        }

        List<Invoice> invoices = invoiceRepository.findByCardOwnerAndMonthAndYearAndDeletedAtIsNull(person, latest.getMonth(), latest.getYear());
        BigDecimal totalInvoices = invoices.stream()
                .map(Invoice::getTotalAmount)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal utilizationPercent = totalInvoices
                .multiply(BigDecimal.valueOf(100))
                .divide(totalLimit, 2, RoundingMode.HALF_UP);

        double u = utilizationPercent.doubleValue();

        if (u <= 30) return 100;
        if (u <= 50) return 90;
        if (u <= 70) return 70;
        if (u <= 90) return 50;
        return 20;
    }

    private int calculateOnTimePayment(Person person) {
        List<Invoice> invoices = invoiceRepository.findByCardOwnerAndDeletedAtIsNull(person);
        if (invoices.isEmpty()) {
            return 100;
        }

        LocalDate today = LocalDate.now();
        List<Invoice> relevant = invoices.stream()
                .filter(inv -> inv.getDueDate() != null && inv.getDueDate().isBefore(today))
                .filter(inv -> inv.getStatus() != InvoiceStatus.OPEN)
                .toList();

        if (relevant.isEmpty()) {
            return 100;
        }

        long onTime = relevant.stream()
                .filter(inv -> inv.getStatus() == InvoiceStatus.PAID)
                .filter(inv -> inv.getPaidDate() != null && !inv.getPaidDate().isAfter(inv.getDueDate()))
                .count();

        double pct = (onTime * 100.0) / relevant.size();

        if (pct >= 100) return 100;
        if (pct >= 90) return 90;
        if (pct >= 80) return 70;
        if (pct >= 70) return 50;
        return 20;
    }

    private int calculateSpendingDiversity(Person person) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(Math.max(1, diversityLookbackDays));

        List<FinancialTransaction> txs = transactionRepository.findByPersonAndTransactionDateBetweenAndDeletedAtIsNull(person, start, end);

        Set<String> categories = new HashSet<>();
        for (FinancialTransaction tx : txs) {
            if (tx == null) continue;
            if (tx.getType() != TransactionType.EXPENSE) continue;
            if (tx.getStatus() == TransactionStatus.CANCELLED) continue;

            String category = tx.getCategory();
            if (category == null) continue;
            String normalized = category.trim().toLowerCase(Locale.ROOT);
            if (!normalized.isBlank()) {
                categories.add(normalized);
            }
        }

        int total = Math.max(1, totalCategories);
        double pct = (categories.size() * 100.0) / total;

        if (pct >= 80) return 100;
        if (pct >= 60) return 80;
        if (pct >= 40) return 60;
        if (pct >= 20) return 40;
        return 20;
    }

    private int calculateSpendingConsistency(Person person) {
        int months = Math.max(1, consistencyMonths);

        YearMonth current = YearMonth.from(LocalDate.now());
        List<YearMonth> window = new ArrayList<>();
        for (int i = months - 1; i >= 0; i--) {
            window.add(current.minusMonths(i));
        }

        LocalDate start = window.getFirst().atDay(1);
        LocalDate end = window.getLast().atEndOfMonth();

        List<FinancialTransaction> txs = transactionRepository.findByPersonAndTransactionDateBetweenAndDeletedAtIsNull(person, start, end);

        Map<YearMonth, BigDecimal> totals = new HashMap<>();
        for (YearMonth ym : window) {
            totals.put(ym, BigDecimal.ZERO);
        }

        for (FinancialTransaction tx : txs) {
            if (tx == null) continue;
            if (tx.getType() != TransactionType.EXPENSE) continue;
            if (tx.getStatus() == TransactionStatus.CANCELLED) continue;
            if (tx.getTransactionDate() == null) continue;
            if (tx.getAmount() == null) continue;

            YearMonth ym = YearMonth.from(tx.getTransactionDate());
            if (!totals.containsKey(ym)) continue;

            BigDecimal currentTotal = totals.getOrDefault(ym, BigDecimal.ZERO);
            totals.put(ym, currentTotal.add(tx.getAmount().abs()));
        }

        double[] values = new double[window.size()];
        for (int i = 0; i < window.size(); i++) {
            values[i] = totals.get(window.get(i)).doubleValue();
        }

        double mean = mean(values);
        if (mean == 0) {
            return 100;
        }

        double stdDev = stdDev(values, mean);
        double ratioPct = (stdDev / mean) * 100.0;

        if (ratioPct < 10) return 100;
        if (ratioPct < 20) return 80;
        if (ratioPct < 30) return 60;
        if (ratioPct < 40) return 40;
        return 20;
    }

    private int calculateCreditHistory(Person person) {
        Invoice first = invoiceRepository.findTopByCardOwnerAndDeletedAtIsNullOrderByDueDateAsc(person)
                .orElse(null);

        if (first == null || first.getDueDate() == null) {
            return 20;
        }

        long days = ChronoUnit.DAYS.between(first.getDueDate(), LocalDate.now());
        double years = days / 365.25;

        if (years > 5) return 100;
        if (years >= 3) return 90;
        if (years >= 1) return 70;
        if (years >= 0.5) return 50;
        return 20;
    }

    private static int clampScore(int v) {
        return Math.min(100, Math.max(0, v));
    }

    private static double mean(double[] values) {
        if (values.length == 0) return 0;
        double sum = 0;
        for (double v : values) sum += v;
        return sum / values.length;
    }

    private static double stdDev(double[] values, double mean) {
        if (values.length == 0) return 0;
        double sumSq = 0;
        for (double v : values) {
            double d = v - mean;
            sumSq += d * d;
        }
        return Math.sqrt(sumSq / values.length);
    }
}
