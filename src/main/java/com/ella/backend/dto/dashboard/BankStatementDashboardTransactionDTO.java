package com.ella.backend.dto.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.ella.backend.entities.BankStatementTransaction;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankStatementDashboardTransactionDTO {
    private String id;
    private LocalDate transactionDate;
    private String description;
    private BigDecimal amount;
    private BigDecimal balance;
    private BankStatementTransaction.Type type;
}
