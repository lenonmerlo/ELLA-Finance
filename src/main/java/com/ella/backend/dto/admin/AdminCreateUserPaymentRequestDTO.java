package com.ella.backend.dto.admin;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.ella.backend.enums.Currency;
import com.ella.backend.enums.PaymentProvider;
import com.ella.backend.enums.Plan;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminCreateUserPaymentRequestDTO {

    @NotNull(message = "Plano é obrigatório")
    private Plan plan;

    @NotNull(message = "Valor é obrigatório")
    @DecimalMin(value = "0.01", message = "Valor deve ser maior que zero")
    private BigDecimal amount;

    @NotNull(message = "Moeda é obrigatória")
    private Currency currency;

    /**
     * Por padrão, INTERNAL.
     */
    private PaymentProvider provider = PaymentProvider.INTERNAL;

    /**
     * Data/hora do pagamento (manual). Se não informado, usa agora.
     */
    private LocalDateTime paidAt;

    private String providerPaymentId;

    private String providerRawStatus;
}
