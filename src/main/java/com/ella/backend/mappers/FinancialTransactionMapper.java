package com.ella.backend.mappers;

import com.ella.backend.dto.FinancialTransactionRequestDTO;
import com.ella.backend.dto.FinancialTransactionResponseDTO;
import com.ella.backend.entities.FinancialTransaction;
import com.ella.backend.entities.Person;

public class FinancialTransactionMapper {

    private FinancialTransactionMapper() {}

    public static FinancialTransaction toEntity(FinancialTransactionRequestDTO dto, Person person) {
        return FinancialTransaction.builder()
                .person(person)
                .description(dto.description())
                .amount(dto.amount())
                .type(dto.type())
                .category(dto.category())
                .transactionDate(dto.transactionDate())
                .dueDate(dto.dueDate())
                .paidDate(dto.paidDate())
                .status(dto.status())
                .build();
    }

    public static void updateEntity(FinancialTransaction entity, FinancialTransactionRequestDTO dto, Person person) {
        entity.setPerson(person);
        entity.setDescription(dto.description());
        entity.setAmount(dto.amount());
        entity.setType(dto.type());
        entity.setCategory(dto.category());
        entity.setTransactionDate(dto.transactionDate());
        entity.setDueDate(dto.dueDate());
        entity.setPaidDate(dto.paidDate());
        entity.setStatus(dto.status());
    }

    public static FinancialTransactionResponseDTO toResponseDTO(FinancialTransaction entity) {
        return new FinancialTransactionResponseDTO(
                entity.getId(),
                entity.getPerson().getId(),
                entity.getPerson().getName(),
                entity.getDescription(),
                entity.getAmount(),
                entity.getType(),
                entity.getCategory(),
                entity.getTransactionDate(),
                entity.getDueDate(),
                entity.getPaidDate(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
