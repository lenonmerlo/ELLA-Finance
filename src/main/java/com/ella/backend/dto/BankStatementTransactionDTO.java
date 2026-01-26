package com.ella.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.ella.backend.entities.BankStatementTransaction;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class BankStatementTransactionDTO {

    private LocalDate transactionDate;
    private String description;
    private BigDecimal amount;
    private BigDecimal balance;
    private BankStatementTransaction.Type type;

    public static BankStatementTransactionDTO from(BankStatementTransaction tx) {
        if (tx == null) return null;
        return new BankStatementTransactionDTO(
                tx.getTransactionDate(),
                tx.getDescription(),
                tx.getAmount(),
                tx.getBalance(),
                tx.getType()
        );
    }
}
