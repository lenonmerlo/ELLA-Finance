package com.ella.backend.dto.admin;

import com.ella.backend.enums.Plan;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminUpdateUserPlanRequestDTO {
    @NotNull
    private Plan plan;
}
