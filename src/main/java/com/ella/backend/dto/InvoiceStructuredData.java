package com.ella.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.ella.backend.services.invoices.parsers.TransactionData;

import lombok.Data;

@Data
public class InvoiceStructuredData {
    private String cardName;
    private String cardholder;
    private String lastFourDigits;
    private LocalDate dueDate;
    private BigDecimal totalAmount;
    private List<TransactionData> transactions;

    public InvoiceStructuredData() {
    }
}
