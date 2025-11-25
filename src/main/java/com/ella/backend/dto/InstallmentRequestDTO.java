// src/main/java/com/ella/backend/dto/InstallmentRequestDTO.java
package com.ella.backend.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class InstallmentRequestDTO {

    @NotBlank(message = "invoiceId é obrigatório")
    private String invoiceId;

    @NotBlank(message = "transactionId é obrigatório")
    private String transactionId;

    @NotNull(message = "Número da parcela é obrigatório")
    @Min(1)
    private Integer number;

    @NotNull(message = "Total de parcelas é obrigatório")
    @Min(1)
    private Integer total;

    @NotNull(message = "Valor da parcela é obrigatório")
    @DecimalMin(value = "0.01", message = "Valor deve ser maior que zero")
    private BigDecimal amount;

    @NotNull(message = "Data de vencimento é obrigatória")
    private LocalDate dueDate;
}
