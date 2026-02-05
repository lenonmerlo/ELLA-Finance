package com.ella.backend.services.cashflow;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.ella.backend.entities.BankStatementTransaction;
import com.ella.backend.entities.FinancialTransaction;
import com.ella.backend.entities.Person;
import com.ella.backend.enums.TransactionStatus;
import com.ella.backend.enums.TransactionType;
import com.ella.backend.repositories.BankStatementTransactionRepository;
import com.ella.backend.repositories.FinancialTransactionRepository;

@Service
public class CashflowTransactionsService {

    private final FinancialTransactionRepository financialTransactionRepository;
    private final BankStatementTransactionRepository bankStatementTransactionRepository;

    public CashflowTransactionsService(
            FinancialTransactionRepository financialTransactionRepository,
            BankStatementTransactionRepository bankStatementTransactionRepository
    ) {
        this.financialTransactionRepository = financialTransactionRepository;
        this.bankStatementTransactionRepository = bankStatementTransactionRepository;
    }

    /**
     * Returns a best-effort combined list of transactions for cashflow analytics.
     *
     * - Base: FinancialTransaction (already categorized)
     * - Plus: BankStatementTransaction mapped into synthetic FinancialTransaction
     *
     * Notes:
     * - Bank statement rows like BALANCE are ignored.
     * - Some statement rows that look like credit card bill payments are ignored to reduce double counting.
     */
    public List<FinancialTransaction> fetchCashflowTransactions(Person person, LocalDate start, LocalDate end) {
        if (person == null || person.getId() == null || start == null || end == null) {
            return List.of();
        }

        List<FinancialTransaction> base = financialTransactionRepository
            .findByPersonAndTransactionDateBetweenAndDeletedAtIsNull(person, start, end);
        if (base == null) {
            base = List.of();
        }

        List<BankStatementTransaction> statement = bankStatementTransactionRepository
                .findForUserAndPeriod(person.getId(), start, end);
        if (statement == null || statement.isEmpty()) {
            return base;
        }

        List<FinancialTransaction> mapped = new ArrayList<>(statement.size());
        for (BankStatementTransaction stx : statement) {
            FinancialTransaction fx = toSyntheticFinancialTransaction(person, stx);
            if (fx != null) {
                mapped.add(fx);
            }
        }

        if (mapped.isEmpty()) {
            return base;
        }

        List<FinancialTransaction> out = new ArrayList<>(base.size() + mapped.size());
        out.addAll(base);
        out.addAll(mapped);
        out.sort(Comparator.comparing(FinancialTransaction::getTransactionDate, Comparator.nullsLast(Comparator.naturalOrder())));
        return out;
    }

    private FinancialTransaction toSyntheticFinancialTransaction(Person person, BankStatementTransaction stx) {
        if (person == null || stx == null) {
            return null;
        }
        if (stx.getType() == null || stx.getTransactionDate() == null || stx.getAmount() == null) {
            return null;
        }
        if (stx.getType() == BankStatementTransaction.Type.BALANCE) {
            return null;
        }

        String description = stx.getDescription() != null ? stx.getDescription().trim() : "";
        if (description.isBlank()) {
            return null;
        }

        if (BankStatementCashflowHeuristics.looksLikeCreditCardBillPayment(stx.getType(), description)) {
            return null;
        }

        TransactionType type = stx.getType() == BankStatementTransaction.Type.CREDIT
                ? TransactionType.INCOME
                : TransactionType.EXPENSE;

        String category = BankStatementCashflowHeuristics.categorize(description, stx.getType());

        return FinancialTransaction.builder()
                .person(person)
                .transactionDate(stx.getTransactionDate())
                .purchaseDate(stx.getTransactionDate())
                .description(description)
                .amount(stx.getAmount())
                .type(type)
                .category(category)
                .status(TransactionStatus.PAID)
                .build();
    }
}
