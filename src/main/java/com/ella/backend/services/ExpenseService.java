// src/main/java/com/ella/backend/services/ExpenseService.java
package com.ella.backend.services;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.ella.backend.audit.Auditable;
import com.ella.backend.dto.ExpenseRequestDTO;
import com.ella.backend.dto.ExpenseResponseDTO;
import com.ella.backend.entities.FinancialTransaction;
import com.ella.backend.entities.Person;
import com.ella.backend.enums.TransactionStatus;
import com.ella.backend.enums.TransactionType;
import com.ella.backend.exceptions.BadRequestException;
import com.ella.backend.exceptions.ResourceNotFoundException;
import com.ella.backend.repositories.FinancialTransactionRepository;
import com.ella.backend.repositories.PersonRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final FinancialTransactionRepository transactionRepository;
    private final PersonRepository personRepository;

    @Auditable(action = "EXPENSE_CREATED", entityType = "FinancialTransaction")
    @Transactional
    @CacheEvict(cacheNames = "dashboard", allEntries = true)
    public ExpenseResponseDTO create(ExpenseRequestDTO dto) {
        validateExpenseBusinessRules(dto);

        UUID personUuid = UUID.fromString(dto.getPersonId());
        Person person = personRepository.findById(personUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa não encontrada"));

        FinancialTransaction entity = FinancialTransaction.builder()
                .person(person)
                .description(dto.getDescription())
                .amount(dto.getAmount())
                .type(TransactionType.EXPENSE)
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

        FinancialTransaction entity = transactionRepository.findByIdAndDeletedAtIsNull(uuid)
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

        List<FinancialTransaction> list = transactionRepository.findByPersonAndDeletedAtIsNull(person);

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
            .findByPersonAndTransactionDateBetweenAndDeletedAtIsNull(person, start, end);

        return list.stream()
                .filter(tx -> tx.getType() == TransactionType.EXPENSE)
                .map(this::toDTO)
                .toList();
    }
    public Page<ExpenseResponseDTO> findByPersonPaginated(String personId, int page, int size) {
        UUID personUuid = UUID.fromString(personId);
        Person person = personRepository.findById(personUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa não encontrada"));

        Pageable pageable = PageRequest.of(page, size, Sort.by("transactionDate").descending());

        return transactionRepository
            .findByPersonAndTypeAndDeletedAtIsNull(person, TransactionType.EXPENSE, pageable)
                .map(this::toDTO);
    }

    @Auditable(action = "EXPENSE_UPDATED", entityType = "FinancialTransaction")
    @Transactional
    @CacheEvict(cacheNames = "dashboard", allEntries = true)
    public ExpenseResponseDTO update(String id, ExpenseRequestDTO dto) {
        validateExpenseBusinessRules(dto);

        UUID uuid = UUID.fromString(id);

        FinancialTransaction entity = transactionRepository.findByIdAndDeletedAtIsNull(uuid)
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

    @Auditable(action = "EXPENSE_DELETED", entityType = "FinancialTransaction")
    @Transactional
    @CacheEvict(cacheNames = "dashboard", allEntries = true)
    public void delete(String id) {
        UUID uuid = UUID.fromString(id);

        FinancialTransaction entity = transactionRepository.findByIdAndDeletedAtIsNull(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Despesa não encontrada"));

        if (entity.getType() != TransactionType.EXPENSE) {
            throw new ResourceNotFoundException("Despesa não encontrada");
        }

        transactionRepository.delete(entity);
    }

    private void validateExpenseBusinessRules(ExpenseRequestDTO dto) {
        if (dto.getAmount() == null || dto.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Valor da despesa deve ser maior que zero");
        }

        LocalDate txDate = dto.getTransactionDate();
        LocalDate dueDate = dto.getDueDate();
        LocalDate paidDate = dto.getPaidDate();

        if (dueDate != null && dueDate.isBefore(txDate)) {
            throw new BadRequestException("Data de vencimento não pode ser anterior à data da transação");
        }

        if (paidDate != null && paidDate.isBefore(txDate)) {
            throw new BadRequestException("Data de pagamento não pode ser anterior à data da transação");
        }

        if (dto.getStatus() == TransactionStatus.PAID && paidDate == null) {
            throw new BadRequestException("Despesas com status PAID devem ter data de pagamento");
        }

        if (paidDate != null && dto.getStatus() != TransactionStatus.PAID) {
            throw new BadRequestException("Se a data de pagamento foi informada, o status deve ser PAID");
        }
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
