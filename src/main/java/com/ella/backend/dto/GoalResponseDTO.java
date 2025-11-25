package com.ella.backend.dto;

import com.ella.backend.enums.GoalStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class GoalResponseDTO {

    private String id;
    private String title;
    private String description;
    private BigDecimal targetAmount;
    private BigDecimal currentAmount;
    private LocalDate deadline;
    private GoalStatus status;

    private String ownerId;
    private String ownerName;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
