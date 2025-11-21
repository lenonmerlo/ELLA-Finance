package com.ella.backend.dto;

import com.ella.backend.enums.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class UserResponseDTO {
    private String id;

    // Person
    private String name;
    private String phone;
    private LocalDate birthDate;
    private String address;
    private BigDecimal income;

    private Language language;
    private Plan plan;
    private Currency currency;
    private Status status;

    // User
    private String email;
    private Role role;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
