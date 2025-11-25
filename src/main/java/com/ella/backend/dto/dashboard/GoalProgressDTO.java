package com.ella.backend.dto.dashboard;

import com.ella.backend.enums.GoalStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

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

    private GoalStatus status; // Mesma enum usada em Goal
}
