package com.ella.backend.dto.dashboard;

import java.math.BigDecimal;

import com.ella.backend.enums.GoalStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoalProgressDTO {

    private String goalId;
    private String title;

    private BigDecimal targetAmount;
    private BigDecimal currentAmount;
    private BigDecimal percentage;
    private java.time.LocalDate deadline;

    private GoalStatus status; // Mesma enum usada em Goal
}