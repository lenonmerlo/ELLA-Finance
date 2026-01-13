package com.ella.backend.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.ella.backend.dto.dashboard.SummaryDTO;
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
public class DashboardSummaryService {

    private final PersonRepository personRepository;
    private final FinancialTransactionRepository financialTransactionRepository;

        @Value("${ella.dashboard.debug.summary-expenses:false}")
        private boolean debugSummaryExpenses;

        @Value("${ella.dashboard.debug.summary-expenses-max-items:60}")
        private int debugSummaryExpensesMaxItems;

    public SummaryDTO getSummary(String personId, int year, int month) {
        UUID personUuid = UUID.fromString(personId);
        Person person = personRepository.findById(personUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Person not found"));

        YearMonth ym = YearMonth.of(year, month);
        LocalDate monthStart = ym.atDay(1);
        LocalDate monthEnd = ym.atEndOfMonth();

        List<FinancialTransaction> monthTx = financialTransactionRepository.findByPersonAndTransactionDateBetween(
                person, monthStart, monthEnd
        );

        BigDecimal income = sumByType(monthTx, TransactionType.INCOME);

        BigDecimal expenses;
        if (debugSummaryExpenses) {
            List<FinancialTransaction> expensesTx = monthTx.stream()
                    .filter(tx -> tx != null && tx.getType() == TransactionType.EXPENSE)
                    .toList();
            expenses = sumAmounts(expensesTx);
            logSummaryExpensesDebug(personId, year, month, monthStart, monthEnd, monthTx.size(), expensesTx, expenses);
        } else {
            expenses = sumByType(monthTx, TransactionType.EXPENSE);
        }

        BigDecimal balance = income.subtract(expenses);
        
        // Calculate savings rate
        int savingsRate = 0;
        if (income.compareTo(BigDecimal.ZERO) > 0) {
            savingsRate = balance.multiply(BigDecimal.valueOf(100))
                    .divide(income, 0, RoundingMode.HALF_UP).intValue();
        }

        return SummaryDTO.builder()
                .totalIncome(income)
                .totalExpenses(expenses)
                .balance(balance)
                .savingsRate(savingsRate)
                .build();
    }

    private BigDecimal sumByType(List<FinancialTransaction> txs, TransactionType type) {
        return txs.stream()
                .filter(tx -> tx.getType() == type)
                .map(FinancialTransaction::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

        private BigDecimal sumAmounts(List<FinancialTransaction> txs) {
                return txs.stream()
                                .map(FinancialTransaction::getAmount)
                                .filter(Objects::nonNull)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        private void logSummaryExpensesDebug(
                        String personId,
                        int year,
                        int month,
                        LocalDate monthStart,
                        LocalDate monthEnd,
                        int monthTxCount,
                        List<FinancialTransaction> expensesTx,
                        BigDecimal totalExpenses
        ) {
                int max = Math.max(0, debugSummaryExpensesMaxItems);
                int totalCount = expensesTx != null ? expensesTx.size() : 0;

                log.info(
                                "[DashboardSummary][Debug] personId={} period={}-{} monthStart={} monthEnd={} monthTxCount={} expensesCount={} totalExpenses={}",
                                personId,
                                year,
                                String.format("%02d", month),
                                monthStart,
                                monthEnd,
                                monthTxCount,
                                totalCount,
                                totalExpenses
                );

                if (expensesTx == null || expensesTx.isEmpty() || max == 0) return;

                int printed = 0;
                for (FinancialTransaction tx : expensesTx) {
                        if (tx == null) continue;
                        if (printed >= max) break;

                        log.info(
                                        "[DashboardSummary][Debug] EXPENSE id={} amount={} transactionDate={} purchaseDate={} dueDate={} scope={} category='{}' desc='{}'",
                                        tx.getId(),
                                        tx.getAmount(),
                                        tx.getTransactionDate(),
                                        tx.getPurchaseDate(),
                                        tx.getDueDate(),
                                        tx.getScope(),
                                        safe(tx.getCategory(), 40),
                                        safe(tx.getDescription(), 120)
                        );
                        printed++;
                }

                if (totalCount > printed) {
                        log.info("[DashboardSummary][Debug] ... (mostrando {} de {} despesas)", printed, totalCount);
                }
        }

        private static String safe(String value, int maxLen) {
                if (value == null) return "";
                String v = value.replaceAll("\\s+", " ").trim();
                if (maxLen <= 0) return "";
                if (v.length() <= maxLen) return v;
                return v.substring(0, maxLen) + "...";
        }
}
