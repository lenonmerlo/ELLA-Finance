package com.ella.backend.repositories;

import com.ella.backend.entities.FinancialTransaction;
import com.ella.backend.entities.Person;
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
}
