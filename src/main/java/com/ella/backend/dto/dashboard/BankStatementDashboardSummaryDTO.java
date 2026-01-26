package com.ella.backend.dto.dashboard;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankStatementDashboardSummaryDTO {
    private BigDecimal openingBalance;
    private BigDecimal closingBalance;
    private BigDecimal totalIncome;
    private BigDecimal totalExpenses;
    /** Net change for the period (income - expenses). */
    private BigDecimal balance;
    private Integer transactionCount;
}
