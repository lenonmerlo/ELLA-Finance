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
public class MonthlyPointDTO {

    // Exemplo de formato: "2025-01" ou "JAN/2025"
    private String monthLabel;

    private BigDecimal income;
    private BigDecimal expenses;
    private BigDecimal incomeChecking;
    private BigDecimal expensesChecking;
    private BigDecimal expensesCard;
}