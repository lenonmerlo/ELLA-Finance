// src/main/java/com/ella/backend/services/ExpenseService.java
package com.ella.backend.services;

import com.ella.backend.dto.ExpenseRequestDTO;
import com.ella.backend.dto.ExpenseResponseDTO;
import com.ella.backend.entities.FinancialTransaction;
import com.ella.backend.entities.Person;
import com.ella.backend.enums.TransactionType;
import com.ella.backend.exceptions.ResourceNotFoundException;
import com.ella.backend.repositories.FinancialTransactionRepository;
import com.ella.backend.repositories.PersonRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final FinancialTransactionRepository transactionRepository;
    private final PersonRepository personRepository;

    @Transactional
    public ExpenseResponseDTO create(ExpenseRequestDTO dto) {
        UUID personUuid = UUID.fromString(dto.getPersonId());
        Person person = personRepository.findById(personUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa não encontrada"));

        FinancialTransaction entity = FinancialTransaction.builder()
                .person(person)
                .description(dto.getDescription())
                .amount(dto.getAmount())
                .type(TransactionType.EXPENSE)      // 👈 sempre despesa
                .category(dto.getCategory())
                .transactionDate(dto.getTransactionDate())
                .dueDate(dto.getDueDate())
                .paidDate(dto.getPaidDate())
                .status(dto.getStatus())
                .build();

        entity = transactionRepository.save(entity);
        return toDTO(entity);
    }

    public ExpenseResponseDTO findById(String id) {
        UUID uuid = UUID.fromString(id);

        FinancialTransaction entity = transactionRepository.findById(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Despesa não encontrada"));

        if (entity.getType() != TransactionType.EXPENSE) {
            throw new ResourceNotFoundException("Despesa não encontrada");
        }

        return toDTO(entity);
    }

    public List<ExpenseResponseDTO> findByPerson(String personId) {
        UUID personUuid = UUID.fromString(personId);
        Person person = personRepository.findById(personUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa não encontrada"));

        List<FinancialTransaction> list = transactionRepository.findByPerson(person);

        return list.stream()
                .filter(tx -> tx.getType() == TransactionType.EXPENSE)
                .map(this::toDTO)
                .toList();
    }

    public List<ExpenseResponseDTO> findByPersonAndPeriod(String personId, LocalDate start, LocalDate end) {
        UUID personUuid = UUID.fromString(personId);
        Person person = personRepository.findById(personUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa não encontrada"));

        List<FinancialTransaction> list = transactionRepository
                .findByPersonAndTransactionDateBetween(person, start, end);

        return list.stream()
                .filter(tx -> tx.getType() == TransactionType.EXPENSE)
                .map(this::toDTO)
                .toList();
    }

    @Transactional
    public ExpenseResponseDTO update(String id, ExpenseRequestDTO dto) {
        UUID uuid = UUID.fromString(id);

        FinancialTransaction entity = transactionRepository.findById(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Despesa não encontrada"));

        if (entity.getType() != TransactionType.EXPENSE) {
            throw new ResourceNotFoundException("Despesa não encontrada");
        }

        UUID personUuid = UUID.fromString(dto.getPersonId());
        Person person = personRepository.findById(personUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa não encontrada"));

        entity.setPerson(person);
        entity.setDescription(dto.getDescription());
        entity.setAmount(dto.getAmount());
        entity.setCategory(dto.getCategory());
        entity.setTransactionDate(dto.getTransactionDate());
        entity.setDueDate(dto.getDueDate());
        entity.setPaidDate(dto.getPaidDate());
        entity.setStatus(dto.getStatus());

        entity = transactionRepository.save(entity);
        return toDTO(entity);
    }

    @Transactional
    public void delete(String id) {
        UUID uuid = UUID.fromString(id);

        FinancialTransaction entity = transactionRepository.findById(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Despesa não encontrada"));

        if (entity.getType() != TransactionType.EXPENSE) {
            throw new ResourceNotFoundException("Despesa não encontrada");
        }

        transactionRepository.delete(entity);
    }

    private ExpenseResponseDTO toDTO(FinancialTransaction entity) {
        ExpenseResponseDTO dto = new ExpenseResponseDTO();

        dto.setId(entity.getId().toString());
        dto.setPersonId(entity.getPerson().getId().toString());
        dto.setPersonName(entity.getPerson().getName());

        dto.setDescription(entity.getDescription());
        dto.setAmount(entity.getAmount());
        dto.setCategory(entity.getCategory());
        dto.setTransactionDate(entity.getTransactionDate());
        dto.setDueDate(entity.getDueDate());
        dto.setPaidDate(entity.getPaidDate());
        dto.setStatus(entity.getStatus());

        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());

        return dto;
    }
}
