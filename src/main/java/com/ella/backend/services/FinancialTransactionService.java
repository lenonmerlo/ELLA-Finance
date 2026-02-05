package com.ella.backend.services;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ella.backend.audit.Auditable;
import com.ella.backend.dto.FinancialTransactionRequestDTO;
import com.ella.backend.dto.FinancialTransactionResponseDTO;
import com.ella.backend.dto.TransactionBulkUpdateRequest;
import com.ella.backend.entities.FinancialTransaction;
import com.ella.backend.entities.Person;
import com.ella.backend.enums.TransactionScope;
import com.ella.backend.exceptions.ResourceNotFoundException;
import com.ella.backend.mappers.FinancialTransactionMapper;
import com.ella.backend.repositories.FinancialTransactionRepository;
import com.ella.backend.repositories.InstallmentRepository;
import com.ella.backend.repositories.PersonRepository;

@Service
@Transactional
public class FinancialTransactionService {

    private final FinancialTransactionRepository transactionRepository;
    private final PersonRepository personRepository;
        private final InstallmentRepository installmentRepository;
        private final CriticalTransactionDetectionService criticalDetectionService;

    public FinancialTransactionService(FinancialTransactionRepository transactionRepository,
                                                                           PersonRepository personRepository,
                                                                                                                                                   InstallmentRepository installmentRepository,
                                                                                                                                                   CriticalTransactionDetectionService criticalDetectionService) {
        this.transactionRepository = transactionRepository;
        this.personRepository = personRepository;
                this.installmentRepository = installmentRepository;
                this.criticalDetectionService = criticalDetectionService;
    }

    @Auditable(action = "TRANSACTION_CREATED", entityType = "FinancialTransaction")
    public FinancialTransactionResponseDTO create(FinancialTransactionRequestDTO dto) {
        UUID personUuid = UUID.fromString(dto.personId());
        Person person = personRepository.findById(personUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa não encontrada"));

        FinancialTransaction entity = FinancialTransactionMapper.toEntity(dto, person);
        criticalDetectionService.evaluateAndApply(entity);
        FinancialTransaction saved = transactionRepository.save(entity);

        return FinancialTransactionMapper.toResponseDTO(saved);
    }

    @Transactional(readOnly = true)
    public FinancialTransactionResponseDTO findById(String id) {
        UUID uuid = UUID.fromString(id);
                FinancialTransaction entity = transactionRepository.findByIdAndDeletedAtIsNull(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Transação não encontrada"));

        return FinancialTransactionMapper.toResponseDTO(entity);
    }

    @Transactional(readOnly = true)
    public List<FinancialTransactionResponseDTO> findByPerson(String personId) {
        UUID personUuid = UUID.fromString(personId);
        Person person = personRepository.findById(personUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa não encontrada"));

        return transactionRepository.findByPersonAndDeletedAtIsNull(person)
                .stream()
                .map(FinancialTransactionMapper::toResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FinancialTransactionResponseDTO> findByPersonAndPeriod(String personId, LocalDate start, LocalDate end) {
        UUID personUuid = UUID.fromString(personId);
        Person person = personRepository.findById(personUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa não encontrada"));

        return transactionRepository.findByPersonAndTransactionDateBetweenAndDeletedAtIsNull(person, start, end)
                .stream()
                .map(FinancialTransactionMapper::toResponseDTO)
                .toList();
    }

    @Auditable(action = "TRANSACTION_UPDATED", entityType = "FinancialTransaction")
    public FinancialTransactionResponseDTO update(String id, FinancialTransactionRequestDTO dto) {
        UUID uuid = UUID.fromString(id);
                FinancialTransaction entity = transactionRepository.findByIdAndDeletedAtIsNull(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Transação não encontrada"));

        UUID personUuid = UUID.fromString(dto.personId());
        Person person = personRepository.findById(personUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa não encontrada"));

        FinancialTransactionMapper.updateEntity(entity, dto, person);
        criticalDetectionService.evaluateAndApply(entity);
        FinancialTransaction updated = transactionRepository.save(entity);

        return FinancialTransactionMapper.toResponseDTO(updated);
    }

    @Auditable(action = "TRANSACTION_DELETED", entityType = "FinancialTransaction")
    public void delete(String id) {
        UUID uuid = UUID.fromString(id);
                FinancialTransaction entity = transactionRepository.findByIdAndDeletedAtIsNull(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Transação não encontrada"));

                // Remove parcelas vinculadas antes de apagar a transação para evitar violação de FK
                var installments = installmentRepository.findByTransaction(entity);
                if (installments != null && !installments.isEmpty()) {
                        installmentRepository.deleteAll(installments);
                }

        transactionRepository.delete(entity);
    }

        @Auditable(action = "TRANSACTION_BULK_UPDATED", entityType = "FinancialTransaction")
        public List<FinancialTransactionResponseDTO> bulkUpdate(TransactionBulkUpdateRequest request) {
                UUID personUuid = UUID.fromString(request.personId());
                Person person = personRepository.findById(personUuid)
                                .orElseThrow(() -> new ResourceNotFoundException("Pessoa não encontrada"));

                return request.updates().stream().map(item -> {
                        UUID txId = UUID.fromString(item.id());
                        FinancialTransaction entity = transactionRepository.findByIdAndDeletedAtIsNull(txId)
                                        .orElseThrow(() -> new ResourceNotFoundException("Transação não encontrada"));

                        if (!entity.getPerson().getId().equals(person.getId())) {
                                throw new ResourceNotFoundException("Transação não pertence à pessoa informada");
                        }

                        if (item.category() != null && !item.category().isBlank()) {
                                entity.setCategory(item.category());
                        }
                        if (item.scope() != null) {
                                entity.setScope(item.scope());
                        } else if (entity.getScope() == null) {
                                entity.setScope(TransactionScope.PERSONAL);
                        }

                        FinancialTransaction saved = transactionRepository.save(entity);
                        return FinancialTransactionMapper.toResponseDTO(saved);
                }).toList();
        }
}
