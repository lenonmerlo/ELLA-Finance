package com.ella.backend.dto.dashboard;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardRequestDTO {

    @NotBlank(message = "O personId é obrigatório")
    private String personId;

    @NotNull(message = "O ano é obrigatório")
    private Integer year;

    @NotNull(message = "O mês é obrigatório")
    private Integer month;
}
