package com.ella.backend.dto.reports;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReportSummaryDTO {
    private BigDecimal totalIncome;
    private BigDecimal totalExpenses;
    private BigDecimal balance;
    private BigDecimal savingsRate;

    private BigDecimal prevTotalIncome;
    private BigDecimal prevTotalExpenses;
    private BigDecimal prevBalance;

    private BigDecimal incomeChange;
    private BigDecimal expensesChange;
    private BigDecimal balanceChange;
}
