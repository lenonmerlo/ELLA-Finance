package com.ella.backend.dto.investment;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.ella.backend.enums.InvestmentType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class InvestmentRequest {

    @NotBlank
    private String name;

    @NotNull
    private InvestmentType type;

    @NotNull
    private BigDecimal initialValue;

    @NotNull
    private BigDecimal currentValue;

    @NotNull
    private LocalDate investmentDate;

    private String description;
}
