package com.ella.backend.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.ella.backend.enums.Currency;
import com.ella.backend.enums.Language;
import com.ella.backend.enums.Plan;
import com.ella.backend.enums.Role;
import com.ella.backend.enums.Status;

import lombok.Data;

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

    // Avatar (data URL: data:<mime>;base64,<...>)
    private String avatarDataUrl;

    private Instant createdAt;
    private Instant updatedAt;
}
