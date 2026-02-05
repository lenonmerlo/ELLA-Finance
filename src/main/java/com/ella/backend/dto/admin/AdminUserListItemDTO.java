package com.ella.backend.dto.admin;

import java.time.LocalDateTime;

import com.ella.backend.enums.Plan;
import com.ella.backend.enums.Role;
import com.ella.backend.enums.Status;

import lombok.Data;

@Data
public class AdminUserListItemDTO {
    private String id;
    private String name;
    private String email;
    private Plan plan;
    private Role role;
    private Status status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
