package com.ella.backend.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceSummaryDTO {

    private String creditCardId;
    private String creditCardName;

    private BigDecimal totalAmount;
    private LocalDate dueDate;

    private Boolean isOverdue;
}
