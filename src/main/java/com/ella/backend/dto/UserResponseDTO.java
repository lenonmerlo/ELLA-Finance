package com.ella.backend.dto;

import com.ella.backend.enums.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

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

    private Instant createdAt;
    private Instant updatedAt;
}
