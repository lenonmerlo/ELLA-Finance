package com.ella.backend.services;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ella.backend.dto.CriticalTransactionResponseDTO;
import com.ella.backend.dto.CriticalTransactionsStatsDTO;
import com.ella.backend.entities.FinancialTransaction;
import com.ella.backend.entities.Person;
import com.ella.backend.enums.CriticalReason;
import com.ella.backend.exceptions.BadRequestException;
import com.ella.backend.exceptions.ResourceNotFoundException;
import com.ella.backend.mappers.FinancialTransactionMapper;
import com.ella.backend.repositories.FinancialTransactionRepository;
import com.ella.backend.repositories.PersonRepository;

@Service
@Transactional
public class CriticalTransactionsService {

    private final FinancialTransactionRepository transactionRepository;
    private final PersonRepository personRepository;

    public CriticalTransactionsService(
            FinancialTransactionRepository transactionRepository,
            PersonRepository personRepository
    ) {
        this.transactionRepository = transactionRepository;
        this.personRepository = personRepository;
    }

    @Transactional(readOnly = true)
    public List<CriticalTransactionResponseDTO> listUnreviewed(String personId) {
        Person person = loadPerson(personId);
        return transactionRepository.findByPersonAndCriticalTrueAndCriticalReviewedFalseAndDeletedAtIsNullOrderByTransactionDateDesc(person)
                .stream()
                .map(this::toCriticalDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CriticalTransactionResponseDTO> listUnreviewedByReason(String personId, CriticalReason reason) {
        Person person = loadPerson(personId);
        return transactionRepository
                .findByPersonAndCriticalTrueAndCriticalReviewedFalseAndCriticalReasonAndDeletedAtIsNullOrderByTransactionDateDesc(person, reason)
                .stream()
                .map(this::toCriticalDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public CriticalTransactionsStatsDTO stats(String personId) {
        Person person = loadPerson(personId);

        long totalCritical = transactionRepository.countByPersonAndCriticalTrueAndDeletedAtIsNull(person);
        long totalUnreviewed = transactionRepository.countByPersonAndCriticalTrueAndCriticalReviewedFalseAndDeletedAtIsNull(person);

        Map<String, Long> byReason = new LinkedHashMap<>();
        for (CriticalReason reason : CriticalReason.values()) {
            long count = transactionRepository.countByPersonAndCriticalTrueAndCriticalReasonAndDeletedAtIsNull(person, reason);
            if (count > 0) {
                byReason.put(reason.name(), count);
            }
        }

        return new CriticalTransactionsStatsDTO(totalCritical, totalUnreviewed, byReason);
    }

    public CriticalTransactionResponseDTO markReviewed(String transactionId) {
        UUID uuid = UUID.fromString(transactionId);
        FinancialTransaction tx = transactionRepository.findByIdAndDeletedAtIsNull(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Transação não encontrada"));

        if (!tx.isCritical()) {
            throw new BadRequestException("Transação não está marcada como crítica");
        }

        tx.setCriticalReviewed(true);
        tx.setCriticalReviewedAt(LocalDateTime.now());

        FinancialTransaction saved = transactionRepository.save(tx);
        return toCriticalDto(saved);
    }

    private Person loadPerson(String personId) {
        UUID personUuid = UUID.fromString(personId);
        return personRepository.findById(personUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa não encontrada"));
    }

    private CriticalTransactionResponseDTO toCriticalDto(FinancialTransaction entity) {
        var base = FinancialTransactionMapper.toResponseDTO(entity);
        return new CriticalTransactionResponseDTO(
                base.id(),
                base.personId(),
                base.personName(),
                base.description(),
                base.amount(),
                base.type(),
                base.scope(),
                base.category(),
                base.tripId(),
                base.tripSubcategory(),
                base.transactionDate(),
                base.purchaseDate(),
                base.dueDate(),
                base.paidDate(),
                base.status(),
                base.createdAt(),
                base.updatedAt(),
                entity.isCritical(),
                entity.getCriticalReason(),
                entity.isCriticalReviewed(),
                entity.getCriticalReviewedAt()
        );
    }
}
