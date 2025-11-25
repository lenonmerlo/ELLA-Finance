package com.ella.backend.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyPointDTO {

    // Exemplo de formato: "2025-01" ou "JAN/2025"
    private String monthLabel;

    private BigDecimal income;
    private BigDecimal expenses;
}
