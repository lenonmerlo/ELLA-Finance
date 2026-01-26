package com.ella.backend.repositories;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ella.backend.entities.BankStatementTransaction;

public interface BankStatementTransactionRepository extends JpaRepository<BankStatementTransaction, UUID> {

    @Query("select t from BankStatementTransaction t "
	    + "where t.bankStatement.userId = :userId "
	    + "and t.transactionDate >= :startDate and t.transactionDate <= :endDate "
	    + "order by t.transactionDate asc")
    List<BankStatementTransaction> findForUserAndPeriod(
	    @Param("userId") UUID userId,
	    @Param("startDate") LocalDate startDate,
	    @Param("endDate") LocalDate endDate);
}
