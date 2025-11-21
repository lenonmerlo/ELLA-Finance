package com.ella.backend.dto;

import com.ella.backend.enums.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class UserRequestDTO {

    // Campos herdados de Person
    private String name;
    private String phone;
    private LocalDate birthDate;
    private String address;
    private BigDecimal income;

    private Language language;
    private Plan plan;
    private Currency currency;
    private Status status;

    // Campos próprios do User
    private String email;
    private String password;
    private Role role;
}
