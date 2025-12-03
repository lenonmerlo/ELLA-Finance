package com.ella.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

@Data
public class UpdateProfileRequestDTO {
    @NotBlank(message = "Nome é obrigatório")
    private String name;

    @Email(message = "Email inválido")
    @NotBlank(message = "Email é obrigatório")
    private String email;

    private String phone;
    private String address;
    private LocalDate birthDate;
}
