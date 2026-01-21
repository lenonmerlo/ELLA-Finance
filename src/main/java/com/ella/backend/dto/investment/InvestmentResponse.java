package com.ella.backend.dto.investment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import com.ella.backend.enums.InvestmentType;

import lombok.Data;

@Data
public class InvestmentResponse {
    private UUID id;
    private String name;
    private InvestmentType type;
    private BigDecimal initialValue;
    private BigDecimal currentValue;
    private LocalDate investmentDate;
    private String description;
    private BigDecimal profitability;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
