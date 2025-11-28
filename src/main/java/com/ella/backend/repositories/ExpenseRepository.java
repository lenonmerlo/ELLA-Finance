package com.ella.backend.repositories;

import com.ella.backend.entities.Expense;
import com.ella.backend.entities.Person;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ExpenseRepository extends JpaRepository<Expense, UUID> {

    // Buscar despesas por pessoa com paginação
    Page<Expense> findByPerson(Person person, Pageable pageable);

    // Buscar despesas por período
    List<Expense> findByPersonAndTransactionDateBetween(
            Person person,
            LocalDate start,
            LocalDate end
    );
}
