package com.ella.backend.services.invoices.parsers;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.ella.backend.enums.TransactionScope;
import com.ella.backend.enums.TransactionType;

public class TransactionData {
    public String description;
    public BigDecimal amount;
    public TransactionType type;
    public TransactionScope scope;
    public String category;
    public LocalDate date;
    public LocalDate dueDate;
    public String cardName;
    public Integer installmentNumber;
    public Integer installmentTotal;

    public TransactionData(
            String description,
            BigDecimal amount,
            TransactionType type,
            String category,
            LocalDate date,
            String cardName,
            TransactionScope scope
    ) {
        this.description = description;
        this.amount = amount;
        this.type = type;
        this.category = category;
        this.date = date;
        this.cardName = cardName;
        this.scope = scope;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }
}
