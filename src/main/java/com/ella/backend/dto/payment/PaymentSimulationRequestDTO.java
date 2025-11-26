package com.ella.backend.dto.payment;

import com.ella.backend.enums.Currency;
import com.ella.backend.enums.PaymentProvider;
import com.ella.backend.enums.Plan;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PaymentSimulationRequestDTO {

    @NotBlank(message = "userId é obrigatório")
    private String userId;

    @NotNull(message = "Plano é obrigatório")
    private Plan plan;

    @NotNull(message = "Valor é obrigatório")
    @DecimalMin(value = "0.01", message = "Valor deve ser maior que zero")
    private BigDecimal amount;

    @NotNull(message = "Moeda é obrigatória")
    private Currency currency;

    /**
     * Por enquanto sempre mandar INTERNAL
     * Futuramente, MERCADO_PAGO, STRIPE, PAGSEGURO, etc...
     */
    @NotNull(message = "Provedor é obrigatório")
    private PaymentProvider provider;
}
