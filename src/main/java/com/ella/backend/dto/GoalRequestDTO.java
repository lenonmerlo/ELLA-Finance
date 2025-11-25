package com.ella.backend.dto;

import com.ella.backend.enums.GoalStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class GoalRequestDTO {

    @NotBlank(message = "O título é obrigatório")
    private String title;

    private String description;

    @NotNull(message = "O valor alvo é obrigatório")
    private BigDecimal targetAmount;

    // opcional: se vier null, começa em 0
    private BigDecimal currentAmount;

    private LocalDate deadline;

    @NotBlank(message = "O ownerId é obrigatório")
    private String ownerId;

    //Opcional: se não vier, assume ACTIVE
    private GoalStatus status;
}
