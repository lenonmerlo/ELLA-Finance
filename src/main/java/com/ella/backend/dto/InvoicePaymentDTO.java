package com.ella.backend.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class InvoicePaymentDTO {

    @NotNull(message = "paid é obrigatório")
    private Boolean paid;

    /**
     * Data de pagamento (manual). Quando paid=true, pode ser informada pelo usuário.
     * Quando paid=false, será ignorada e removida.
     */
    private LocalDate paidDate;
}
