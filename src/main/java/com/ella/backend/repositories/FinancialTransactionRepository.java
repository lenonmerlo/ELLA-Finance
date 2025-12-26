package com.ella.backend.repositories;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.ella.backend.entities.FinancialTransaction;
import com.ella.backend.entities.Person;
import com.ella.backend.enums.TransactionType;

public interface FinancialTransactionRepository extends JpaRepository<FinancialTransaction, UUID> {

    List<FinancialTransaction> findByPerson(Person person);

    List<FinancialTransaction> findByPersonAndTransactionDateBetween(
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

    Page<FinancialTransaction> findByPersonAndTransactionDateBetweenAndCategoryIgnoreCase(
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

        List<FinancialTransaction> findByPersonAndIdIn(Person person, Collection<UUID> ids);
}
