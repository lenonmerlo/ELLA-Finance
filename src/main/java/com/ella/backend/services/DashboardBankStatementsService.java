package com.ella.backend.services;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ella.backend.dto.dashboard.BankStatementDashboardResponseDTO;
import com.ella.backend.dto.dashboard.BankStatementDashboardSummaryDTO;
import com.ella.backend.dto.dashboard.BankStatementDashboardTransactionDTO;
import com.ella.backend.entities.BankStatement;
import com.ella.backend.entities.BankStatementTransaction;
import com.ella.backend.repositories.BankStatementTransactionRepository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service para gerenciar dados de Movimentação C/C (Conta Corrente).
 * Atualmente retorna um placeholder. Será implementado com integrações de extratos bancários.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardBankStatementsService {

    private final BankStatementTransactionRepository bankStatementTransactionRepository;
    private final EntityManager entityManager;

    /**
     * Busca os extratos bancários do usuário para um período específico.
     *
     * @param personId ID da pessoa
     * @param year Ano (opcional)
     * @param month Mês (opcional)
     * @return Dados dos extratos ou placeholder
     */
    public BankStatementDashboardResponseDTO getBankStatements(UUID personId, Integer year, Integer month) {
        if (personId == null) {
            throw new IllegalArgumentException("personId é obrigatório");
        }

        YearMonth ym = resolveYearMonth(year, month);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();

        log.info("[DashboardBankStatementsService] personId={} range {} -> {}", personId, start, end);

        List<BankStatementTransaction> txs = new ArrayList<>();

        // Include a virtual opening line (SALDO ANTERIOR) from the current month statement opening balance.
        // For a statement ending in 31/12, the opening balance is the saldo as of 30/11 (the day before the period).
        BankStatement currentStatement = findStatementForUserAndPeriod(personId, start, end);
        if (currentStatement != null && currentStatement.getOpeningBalance() != null) {
            BankStatementTransaction saldoAnterior = new BankStatementTransaction();
            saldoAnterior.setTransactionDate(start.minusDays(1));
            saldoAnterior.setDescription("SALDO ANTERIOR");
            saldoAnterior.setAmount(BigDecimal.ZERO);
            saldoAnterior.setBalance(currentStatement.getOpeningBalance());
            saldoAnterior.setType(BankStatementTransaction.Type.BALANCE);
            txs.add(saldoAnterior);
        }

        List<BankStatementTransaction> monthTransactions = bankStatementTransactionRepository
            .findForUserAndPeriod(personId, start, end)
            .stream()
            .filter(t -> t == null || t.getType() != BankStatementTransaction.Type.BALANCE)
            .toList();

        txs.addAll(monthTransactions);

        log.info("[DashboardBankStatementsService] loaded {} txs; samples={}",
            txs.size(),
            txs.stream().limit(3).map(t -> t.getTransactionDate() + " " + t.getDescription()).toList());

        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expenses = BigDecimal.ZERO;

        for (BankStatementTransaction t : txs) {
            if (t == null || t.getAmount() == null) continue;
            if (t.getType() == BankStatementTransaction.Type.BALANCE) continue;
            BigDecimal abs = t.getAmount().abs();
            if (t.getType() == BankStatementTransaction.Type.CREDIT) {
                income = income.add(abs);
            } else {
                expenses = expenses.add(abs);
            }
        }

        BigDecimal balance = income.subtract(expenses);

        BankStatementDashboardSummaryDTO summary = BankStatementDashboardSummaryDTO.builder()
            .openingBalance(currentStatement != null ? currentStatement.getOpeningBalance() : null)
            .closingBalance(currentStatement != null ? currentStatement.getClosingBalance() : null)
                .totalIncome(income)
                .totalExpenses(expenses)
                .balance(balance)
                .transactionCount(txs.size())
                .build();

        List<BankStatementDashboardTransactionDTO> mapped = txs.stream()
                .map(t -> BankStatementDashboardTransactionDTO.builder()
                        .id(t.getId() != null ? t.getId().toString() : "")
                        .transactionDate(t.getTransactionDate())
                        .description(t.getDescription())
                        .amount(t.getAmount())
                        .balance(t.getBalance())
                        .type(t.getType())
                        .build())
                .toList();

        return BankStatementDashboardResponseDTO.builder()
                .summary(summary)
                .transactions(mapped)
                .build();
    }

        private BankStatement findStatementForUserAndPeriod(UUID userId, LocalDate startDate, LocalDate endDate) {
        if (userId == null || startDate == null || endDate == null) return null;
        return entityManager
            .createQuery(
                "select b from BankStatement b "
                    + "where b.userId = :userId "
                    + "and b.statementDate >= :startDate and b.statementDate <= :endDate "
                    + "order by b.statementDate desc",
                BankStatement.class)
            .setParameter("userId", userId)
            .setParameter("startDate", startDate)
            .setParameter("endDate", endDate)
            .setMaxResults(1)
            .getResultList()
            .stream()
            .findFirst()
            .orElse(null);
        }

    private static YearMonth resolveYearMonth(Integer year, Integer month) {
        if (year != null && month != null) {
            return YearMonth.of(year, month);
        }
        return YearMonth.now();
    }
}
