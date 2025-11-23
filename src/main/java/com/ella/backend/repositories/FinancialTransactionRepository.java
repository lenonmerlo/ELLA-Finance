package com.ella.backend.repositories;

import com.ella.backend.entities.FinancialTransaction;
import com.ella.backend.entities.Person;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface FinancialTransactionRepository extends JpaRepository<FinancialTransaction, String> {

    List<FinancialTransaction> findByPerson(Person person);

    List<FinancialTransaction> findByPersonAndTransactionDateBetween(
            Person person,
            LocalDate startDate,
            LocalDate endDate
    );
}
