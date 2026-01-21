package com.ella.backend.dto;

import com.ella.backend.enums.*;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class UserRequestDTO {

    // Campos herdados de Person
    @NotBlank(message = "Nome é obrigatório")
    private String name;

    @NotBlank(message = "Telefone é obrigatório")
    private String phone;

    @NotNull(message = "Data de nascimento é obrigatória")
    @Past(message = "Data de nascimento deve estar no passado")
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

    // Campos próprios do User
    @NotBlank(message = "E-mail é obrigatório")
    @Email(message = "Formato de e-mail inválido")
    private String email;

    @NotBlank(message = "Senha é obrigatória")
    private String password;

    private Role role;
}
