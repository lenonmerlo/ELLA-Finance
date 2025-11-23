package com.ella.backend.services;

import com.ella.backend.dto.FinancialTransactionRequestDTO;
import com.ella.backend.dto.FinancialTransactionResponseDTO;
import com.ella.backend.entities.FinancialTransaction;
import com.ella.backend.entities.Person;
import com.ella.backend.exceptions.ResourceNotFoundException;
import com.ella.backend.mappers.FinancialTransactionMapper;
import com.ella.backend.repositories.FinancialTransactionRepository;
import com.ella.backend.repositories.PersonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

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

    public FinancialTransactionResponseDTO create(FinancialTransactionRequestDTO dto) {
        Person person = personRepository.findById(dto.personId())
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa não encontrada"));

        FinancialTransaction entity = FinancialTransactionMapper.toEntity(dto, person);
        FinancialTransaction saved = transactionRepository.save(entity);

        return FinancialTransactionMapper.toResponseDTO(saved);
    }

    @Transactional(readOnly = true)
    public FinancialTransactionResponseDTO findById(String id) {
        FinancialTransaction entity = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transação não encontrada"));

        return FinancialTransactionMapper.toResponseDTO(entity);
    }

    @Transactional(readOnly = true)
    public List<FinancialTransactionResponseDTO> findByPerson(String personId) {
        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa não encontrada"));

        return transactionRepository.findByPerson(person)
                .stream()
                .map(FinancialTransactionMapper::toResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FinancialTransactionResponseDTO> findByPersonAndPeriod(String personId, LocalDate start, LocalDate end) {
        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa não encontrada"));

        return transactionRepository.findByPersonAndTransactionDateBetween(person, start, end)
                .stream()
                .map(FinancialTransactionMapper::toResponseDTO)
                .toList();
    }

    public FinancialTransactionResponseDTO update(String id, FinancialTransactionRequestDTO dto) {
        FinancialTransaction entity = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transação não encontrada"));

        Person person = personRepository.findById(dto.personId())
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa não encontrada"));

        FinancialTransactionMapper.updateEntity(entity, dto, person);
        FinancialTransaction updated = transactionRepository.save(entity);

        return FinancialTransactionMapper.toResponseDTO(updated);
    }

    public void delete(String id) {
        FinancialTransaction entity = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transação não encontrada"));

        transactionRepository.delete(entity);
    }
}
