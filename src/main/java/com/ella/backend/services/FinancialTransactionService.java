package com.ella.backend.services;

import com.ella.backend.dto.FinancialTransactionRequestDTO;
import com.ella.backend.dto.FinancialTransactionResponseDTO;
import com.ella.backend.entities.FinancialTransaction;
import com.ella.backend.entities.Person;
import com.ella.backend.exceptions.ResourceNotFoundException;
import com.ella.backend.mappers.FinancialTransactionMapper;
import com.ella.backend.repositories.FinancialTransactionRepository;
import com.ella.backend.repositories.PersonRepository;
import com.ella.backend.audit.Auditable;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class FinancialTransactionService {

    private final FinancialTransactionRepository transactionRepository;
    private final PersonRepository personRepository;

    public FinancialTransactionService(FinancialTransactionRepository transactionRepository,
                                       PersonRepository personRepository) {
        this.transactionRepository = transactionRepository;
        this.personRepository = personRepository;
    }

    @Auditable(action = "TRANSACTION_CREATED", entityType = "FinancialTransaction")
    public FinancialTransactionResponseDTO create(FinancialTransactionRequestDTO dto) {
        UUID personUuid = UUID.fromString(dto.personId());
        Person person = personRepository.findById(personUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa não encontrada"));

        FinancialTransaction entity = FinancialTransactionMapper.toEntity(dto, person);
        FinancialTransaction saved = transactionRepository.save(entity);

        return FinancialTransactionMapper.toResponseDTO(saved);
    }

    @Transactional(readOnly = true)
    public FinancialTransactionResponseDTO findById(String id) {
        UUID uuid = UUID.fromString(id);
        FinancialTransaction entity = transactionRepository.findById(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Transação não encontrada"));

        return FinancialTransactionMapper.toResponseDTO(entity);
    }

    @Transactional(readOnly = true)
    public List<FinancialTransactionResponseDTO> findByPerson(String personId) {
        UUID personUuid = UUID.fromString(personId);
        Person person = personRepository.findById(personUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa não encontrada"));

        return transactionRepository.findByPerson(person)
                .stream()
                .map(FinancialTransactionMapper::toResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FinancialTransactionResponseDTO> findByPersonAndPeriod(String personId, LocalDate start, LocalDate end) {
        UUID personUuid = UUID.fromString(personId);
        Person person = personRepository.findById(personUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa não encontrada"));

        return transactionRepository.findByPersonAndTransactionDateBetween(person, start, end)
                .stream()
                .map(FinancialTransactionMapper::toResponseDTO)
                .toList();
    }

    @Auditable(action = "TRANSACTION_UPDATED", entityType = "FinancialTransaction")
    public FinancialTransactionResponseDTO update(String id, FinancialTransactionRequestDTO dto) {
        UUID uuid = UUID.fromString(id);
        FinancialTransaction entity = transactionRepository.findById(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Transação não encontrada"));

        UUID personUuid = UUID.fromString(dto.personId());
        Person person = personRepository.findById(personUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa não encontrada"));

        FinancialTransactionMapper.updateEntity(entity, dto, person);
        FinancialTransaction updated = transactionRepository.save(entity);

        return FinancialTransactionMapper.toResponseDTO(updated);
    }

    @Auditable(action = "TRANSACTION_DELETED", entityType = "FinancialTransaction")
    public void delete(String id) {
        UUID uuid = UUID.fromString(id);
        FinancialTransaction entity = transactionRepository.findById(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Transação não encontrada"));

        transactionRepository.delete(entity);
    }
}
