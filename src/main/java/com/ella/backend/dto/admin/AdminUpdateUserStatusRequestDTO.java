package com.ella.backend.dto.admin;

import com.ella.backend.enums.Status;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminUpdateUserStatusRequestDTO {
    @NotNull
    private Status status;
}
