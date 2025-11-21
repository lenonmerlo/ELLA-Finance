package com.ella.backend.dto;

import com.ella.backend.enums.Currency;
import com.ella.backend.enums.Language;
import com.ella.backend.enums.Plan;
import com.ella.backend.enums.Status;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class PersonResponseDTO {

    private String id;

    private String name;
    private String phone;
    private LocalDate birthDate;
    private String address;
    private BigDecimal income;

    private Language language;
    private Plan plan;
    private Currency currency;
    private Status status;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
