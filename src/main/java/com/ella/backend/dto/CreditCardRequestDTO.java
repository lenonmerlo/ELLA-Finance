package com.ella.backend.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreditCardRequestDTO {

    @NotBlank(message = "ownerId é obrigatório")
    private String ownerId; // Person.id

    @NotBlank(message = "Nome do cartão é obrigatório")
    private String name;

    @NotBlank(message = "Bandeira é obrigatória")
    private String brand;

    @NotNull(message = "Limite é obrigatório")
    @DecimalMin(value = "0.01", message = "Limite deve ser maior que zero")
    private BigDecimal limitAmount;

    @NotNull(message = "Dia de fechamento é obrigatório")
    @Min(value = 1) @Max(value = 31)
    private Integer closingDay;

    @NotNull(message = "Dia de vencimento é obrigatório")
    @Min(value = 1) @Max(value = 31)
    private Integer dueDay;
}
