package com.ella.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GoalDTO {
    private String id;
    private String title;
    private String description;
    private String category;
    private BigDecimal targetAmount;
    private BigDecimal currentAmount;
    private BigDecimal savingsPotential;
    private String difficulty;
    private String timeframe;
    private LocalDate deadline;
    private LocalDate targetDate;
    private String status;
}
