package com.ella.backend.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ella.backend.dto.dashboard.SummaryDTO;
import com.ella.backend.entities.FinancialTransaction;
import com.ella.backend.entities.Person;
import com.ella.backend.enums.TransactionType;
import com.ella.backend.exceptions.ResourceNotFoundException;
import com.ella.backend.repositories.FinancialTransactionRepository;
import com.ella.backend.repositories.PersonRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DashboardSummaryService {

    private final PersonRepository personRepository;
    private final FinancialTransactionRepository financialTransactionRepository;

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
        BigDecimal expenses = sumByType(monthTx, TransactionType.EXPENSE);
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
}
