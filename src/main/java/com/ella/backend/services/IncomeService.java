package com.ella.backend.services;

import com.ella.backend.dto.IncomeRequestDTO;
import com.ella.backend.dto.IncomeResponseDTO;
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
public class IncomeService {

    private final FinancialTransactionRepository transactionRepository;
    private final PersonRepository personRepository;

    @Transactional
    public IncomeResponseDTO create(IncomeRequestDTO dto) {
        UUID personUuid = UUID.fromString(dto.getPersonId());
        Person person = personRepository.findById(personUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa não encontrada"));

        FinancialTransaction entity = FinancialTransaction.builder()
                .person(person)
                .description(dto.getDescription())
                .amount(dto.getAmount())
                .type(TransactionType.INCOME)
                .category(dto.getCategory())
                .transactionDate(dto.getTransactionDate())
                .dueDate(dto.getDueDate())
                .paidDate(dto.getPaidDate())
                .status(dto.getStatus())
                .build();

        entity = transactionRepository.save(entity);
        return toDTO(entity);
    }

    public IncomeResponseDTO findById(String id) {
        UUID uuid = UUID.fromString(id);
        FinancialTransaction entity = transactionRepository.findById(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Receita não encontrada"));

        if (entity.getType() != TransactionType.INCOME) {
            throw new ResourceNotFoundException("Receita não encontrada");
        }
        return toDTO(entity);
    }

    public List<IncomeResponseDTO> findByPerson(String personId) {
        UUID personUuid = UUID.fromString(personId);
        Person person = personRepository.findById(personUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa não encontrada"));

        List<FinancialTransaction> list = transactionRepository.findByPerson(person);

        return list.stream()
                .filter(tx -> tx.getType() == TransactionType.INCOME)
                .map(this::toDTO)
                .toList();
    }

    public List<IncomeResponseDTO> findByPersonAndPeriod(String personId, LocalDate start, LocalDate end) {
        UUID personUuid = UUID.fromString(personId);
        Person person = personRepository.findById(personUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa não encontrada"));

        List<FinancialTransaction> list = transactionRepository
                .findByPersonAndTransactionDateBetween(person, start, end);

        return list.stream()
                .filter(tx -> tx.getType() == TransactionType.INCOME)
                .map(this::toDTO)
                .toList();
    }

    @Transactional
    public IncomeResponseDTO update(String id, IncomeRequestDTO dto) {
        UUID uuid = UUID.fromString(id);
        FinancialTransaction entity = transactionRepository.findById(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Receita não encontrada"));

        if (entity.getType() != TransactionType.INCOME) {
            throw new ResourceNotFoundException("Receita não encontrada");
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
                .orElseThrow(() -> new ResourceNotFoundException("Receita não encontrada"));

        if (entity.getType() != TransactionType.INCOME) {
            throw new ResourceNotFoundException("Receita não encontrada");
        }

        transactionRepository.delete(entity);
    }

    private IncomeResponseDTO toDTO(FinancialTransaction entity) {
        IncomeResponseDTO dto = new IncomeResponseDTO();

        dto.setId(entity.getId().toString());
        dto.setPersonId(entity.getPerson().getId().toString());
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
