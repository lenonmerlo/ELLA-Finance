package com.ella.backend.repositories;

import com.ella.backend.entities.FinancialTransaction;
import com.ella.backend.entities.Person;
import com.ella.backend.enums.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

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

    Page<FinancialTransaction> findByPersonAndType(
            Person person,
            TransactionType type,
            Pageable pageable
    );
}
