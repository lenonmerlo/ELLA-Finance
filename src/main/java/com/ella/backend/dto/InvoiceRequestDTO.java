package com.ella.backend.dto;

import com.ella.backend.entities.FinancialTransaction;
import com.ella.backend.enums.InvoiceStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class InvoiceRequestDTO {

    @NotBlank(message = "cardId é obrigatório")
    private String cardId;

    @NotNull(message = "Mês é obrigatório")
    @Min(1) @Max(12)
    private Integer month;

    @NotNull(message = "Ano é obrigatório")
    @Min(2000)
    private Integer year;

    @NotNull(message = "Data de vencimento é obrigatória")
    private LocalDate dueDate;

    // totals podem ser controlados pelo sistema, mas deixei aqui se quiser ajustar manualmente
    private BigDecimal totalAmount;
    private BigDecimal paidAmount;

    private InvoiceStatus status;

    private FinancialTransaction resume;
}
