package com.ella.backend.services;

import com.ella.backend.dto.IncomeRequestDTO;
import com.ella.backend.dto.IncomeResponseDTO;
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
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;


import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IncomeService {

    private final FinancialTransactionRepository transactionRepository;
    private final PersonRepository personRepository;

    @Transactional
    @CacheEvict(cacheNames = "dashboard", allEntries = true)
    public IncomeResponseDTO create(IncomeRequestDTO dto) {
        validateIncomeBusinessRules(dto);

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

    public Page<IncomeResponseDTO> findByPersonPaginated(String personId, int page, int size) {
        UUID personUuid = UUID.fromString(personId);
        Person person = personRepository.findById(personUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa não encontrada"));

        Pageable pageable = PageRequest.of(page, size, Sort.by("transactionDate").descending());

        return transactionRepository
                .findByPersonAndType(person, TransactionType.INCOME, pageable)
                .map(this::toDTO);
    }

    @Transactional
    @CacheEvict(cacheNames = "dashboard", allEntries = true)
    public IncomeResponseDTO update(String id, IncomeRequestDTO dto) {
        validateIncomeBusinessRules(dto);

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
    @CacheEvict(cacheNames = "dashboard", allEntries = true)
    public void delete(String id) {
        UUID uuid = UUID.fromString(id);
        FinancialTransaction entity = transactionRepository.findById(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Receita não encontrada"));

        if (entity.getType() != TransactionType.INCOME) {
            throw new ResourceNotFoundException("Receita não encontrada");
        }

        transactionRepository.delete(entity);
    }

    private void validateIncomeBusinessRules(IncomeRequestDTO dto) {
        if (dto.getAmount() == null || dto.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Valor da receita deve ser maior que zero");
        }

        LocalDate txDate = dto.getTransactionDate();
        LocalDate paidDate = getLocalDate(dto, txDate);

        if (paidDate != null && dto.getStatus() != TransactionStatus.PAID) {
            throw new BadRequestException("Se a data de pagamento foi informada, o status deve ser PAID");
        }
    }

    private static LocalDate getLocalDate(IncomeRequestDTO dto, LocalDate txDate) {
        LocalDate dueDate = dto.getDueDate();
        LocalDate paidDate = dto.getPaidDate();

        if (dueDate != null && dueDate.isBefore(txDate)) {
            throw new BadRequestException("Data de vencimento não pode ser anterior à data da transação");
        }

        if (paidDate != null && paidDate.isBefore(txDate)) {
            throw new BadRequestException("Data de pagamento não pode ser anterior à data da transação");
        }

        if (dto.getStatus() == TransactionStatus.PAID && paidDate == null) {
            throw new BadRequestException("Receitas com status PAID devem ter a data de pagamento");
        }
        return paidDate;
    }

    private IncomeResponseDTO toDTO(FinancialTransaction entity) {
        IncomeResponseDTO dto = new IncomeResponseDTO();

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
