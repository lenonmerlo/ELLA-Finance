package com.ella.backend.dto.admin;

import com.ella.backend.enums.Role;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminUpdateUserRoleRequestDTO {
    @NotNull
    private Role role;
}
