package com.ella.backend.dto;

import com.ella.backend.enums.Currency;
import com.ella.backend.enums.Language;
import com.ella.backend.enums.Plan;
import com.ella.backend.enums.Status;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class PersonRequestDTO {

    private String name;
    private String phone;
    private LocalDate birthDate;
    private String address;
    private BigDecimal income;

    private Language language;
    private Plan plan;
    private Currency currency;
    private Status status;
}
