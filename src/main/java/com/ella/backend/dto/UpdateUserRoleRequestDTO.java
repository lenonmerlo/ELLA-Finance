package com.ella.backend.dto;

import com.ella.backend.enums.Role;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateUserRoleRequestDTO {

    @NotNull(message = "Role é obrigatória")
    private Role role;
}
