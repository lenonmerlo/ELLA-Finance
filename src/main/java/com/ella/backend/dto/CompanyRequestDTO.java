package com.ella.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CompanyRequestDTO {

    @NotBlank(message = "O nome da empresa é obrigatório")
    private String name;

    private String document;
    private String description;

    @NotBlank(message = "O ownerId é obrigatório")
    private String ownerId;
}
