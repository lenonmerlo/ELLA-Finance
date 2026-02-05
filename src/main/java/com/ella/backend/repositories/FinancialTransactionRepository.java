package com.ella.backend.repositories;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.ella.backend.entities.FinancialTransaction;
import com.ella.backend.entities.Person;
import com.ella.backend.enums.CriticalReason;
import com.ella.backend.enums.TransactionType;

public interface FinancialTransactionRepository extends JpaRepository<FinancialTransaction, UUID> {

        Optional<FinancialTransaction> findByIdAndDeletedAtIsNull(UUID id);

    List<FinancialTransaction> findByPerson(Person person);

        List<FinancialTransaction> findByPersonAndDeletedAtIsNull(Person person);

    List<FinancialTransaction> findByPersonAndTransactionDateBetween(
            Person person,
            LocalDate startDate,
            LocalDate endDate
    );

    List<FinancialTransaction> findByPersonAndTransactionDateBetweenAndDeletedAtIsNull(
            Person person,
            LocalDate startDate,
            LocalDate endDate
    );

    Page<FinancialTransaction> findByPersonAndTransactionDateBetween(
            Person person,
            LocalDate startDate,
            LocalDate endDate,
            Pageable pageable
    );

    Page<FinancialTransaction> findByPersonAndTransactionDateBetweenAndDeletedAtIsNull(
            Person person,
            LocalDate startDate,
            LocalDate endDate,
            Pageable pageable
    );

    Page<FinancialTransaction> findByPersonAndTransactionDateBetweenAndCategoryIgnoreCase(
            Person person,
            LocalDate startDate,
            LocalDate endDate,
            String category,
            Pageable pageable
    );

    Page<FinancialTransaction> findByPersonAndTransactionDateBetweenAndCategoryIgnoreCaseAndDeletedAtIsNull(
            Person person,
            LocalDate startDate,
            LocalDate endDate,
            String category,
            Pageable pageable
    );

    Page<FinancialTransaction> findByPersonAndType(
            Person person,
            TransactionType type,
            Pageable pageable
    );

    Page<FinancialTransaction> findByPersonAndTypeAndDeletedAtIsNull(
            Person person,
            TransactionType type,
            Pageable pageable
    );

        List<FinancialTransaction> findByPersonAndIdIn(Person person, Collection<UUID> ids);

        List<FinancialTransaction> findByPersonAndIdInAndDeletedAtIsNull(Person person, Collection<UUID> ids);

    List<FinancialTransaction> findByPersonAndCriticalTrueAndCriticalReviewedFalseOrderByTransactionDateDesc(Person person);

        List<FinancialTransaction> findByPersonAndCriticalTrueAndCriticalReviewedFalseAndDeletedAtIsNullOrderByTransactionDateDesc(Person person);

    List<FinancialTransaction> findByPersonAndCriticalTrueAndCriticalReviewedFalseAndCriticalReasonOrderByTransactionDateDesc(
            Person person,
            CriticalReason criticalReason
    );

    List<FinancialTransaction> findByPersonAndCriticalTrueAndCriticalReviewedFalseAndCriticalReasonAndDeletedAtIsNullOrderByTransactionDateDesc(
            Person person,
            CriticalReason criticalReason
    );

    long countByPersonAndCriticalTrue(Person person);

        long countByPersonAndCriticalTrueAndDeletedAtIsNull(Person person);

    long countByPersonAndCriticalTrueAndCriticalReviewedFalse(Person person);

        long countByPersonAndCriticalTrueAndCriticalReviewedFalseAndDeletedAtIsNull(Person person);

    long countByPersonAndCriticalTrueAndCriticalReason(Person person, CriticalReason criticalReason);

        long countByPersonAndCriticalTrueAndCriticalReasonAndDeletedAtIsNull(Person person, CriticalReason criticalReason);
}
