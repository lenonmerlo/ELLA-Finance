package com.ella.backend.dto.admin;

import com.ella.backend.enums.Plan;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminRenewSubscriptionRequestDTO {

    /**
     * Quantidade de dias a estender a assinatura.
     * Ex: 30.
     */
    @NotNull(message = "days é obrigatório")
    @Min(value = 1, message = "days deve ser no mínimo 1")
    @Max(value = 3650, message = "days deve ser no máximo 3650")
    private Integer days = 30;

    /**
     * Opcional. Por padrão renova usando o plano atual do usuário.
     * Para trocar plano, use a ação de "Trocar plano".
     */
    private Plan plan;
}
