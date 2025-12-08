package com.ella.backend.dto.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceSummaryDTO {

    private String creditCardId;
    private String creditCardName;
    private String creditCardBrand;
    private String creditCardLastFourDigits;
    private String personName;

    private BigDecimal totalAmount;
    private LocalDate dueDate;

    private Boolean isOverdue;
}