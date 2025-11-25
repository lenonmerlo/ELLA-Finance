package com.ella.backend.dto;

import com.ella.backend.enums.Currency;
import com.ella.backend.enums.Language;
import com.ella.backend.enums.Plan;
import com.ella.backend.enums.Status;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class PersonRequestDTO {

    @NotBlank(message = "Nome é obrigatório")
    private String name;

    @NotBlank(message = "Telefone é obrigatório")
    private String phone;

    @NotNull(message = "Data de nascimento é obrigatória")
    @Past(message = "Data de nascimento deve ser no passado")
    private LocalDate birthDate;

    @NotBlank(message = "Endereço é obrigatório")
    private String address;

    @NotNull(message = "Renda é obrigatória")
    @DecimalMin(value = "0.0", message = "Renda não pode ser negativa")
    private BigDecimal income;

    @NotNull(message = "Idioma é obrigatório")
    private Language language;

    @NotNull(message = "Plano é obrigatório")
    private Plan plan;

    @NotNull(message = "Moeda é obrigatória")
    private Currency currency;

    @NotNull(message = "Status é obrigatório")
    private Status status;
}
