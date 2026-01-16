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
    /**
     * Nome do titular do cartão (quando o PDF fornece), separado do nome do banco/cartão.
     */
    public String cardholderName;
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

    public void setCardholderName(String cardholderName) {
        this.cardholderName = cardholderName;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDate getDate() {
        return date;
    }
}
