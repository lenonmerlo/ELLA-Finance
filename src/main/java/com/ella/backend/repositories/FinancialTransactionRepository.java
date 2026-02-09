package com.ella.backend.repositories;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ella.backend.entities.FinancialTransaction;
import com.ella.backend.entities.Person;
import com.ella.backend.enums.CriticalReason;
import com.ella.backend.enums.TransactionType;

public interface FinancialTransactionRepository extends JpaRepository<FinancialTransaction, UUID> {

        interface CategoryTotalProjection {
                String getCategory();
                BigDecimal getTotal();
        }

        interface MonthTypeTotalProjection {
                LocalDate getMonthStart();
                String getType();
                BigDecimal getTotal();
        }

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

    @Query("""
            select coalesce(sum(t.amount), 0)
            from FinancialTransaction t
            where t.person = :person
              and t.deletedAt is null
              and t.transactionDate between :startDate and :endDate
              and t.type = :type
            """)
    BigDecimal sumAmountByPersonAndDateRangeAndType(
            @Param("person") Person person,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("type") TransactionType type
    );

    @Query("""
            select t.category as category, coalesce(sum(t.amount), 0) as total
            from FinancialTransaction t
            where t.person = :person
              and t.deletedAt is null
              and t.transactionDate between :startDate and :endDate
              and t.type = com.ella.backend.enums.TransactionType.EXPENSE
            group by t.category
            """)
    List<CategoryTotalProjection> sumExpenseTotalsByCategoryForPersonAndDateRange(
            @Param("person") Person person,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query(value = """
            select date_trunc('month', transaction_date)::date as month_start,
                   type as type,
                   coalesce(sum(amount), 0) as total
            from financial_transactions
            where person_id = :personId
              and deleted_at is null
              and transaction_date between :startDate and :endDate
            group by 1, 2
            order by 1
            """, nativeQuery = true)
    List<MonthTypeTotalProjection> sumTotalsByMonthAndTypeForPersonAndDateRange(
            @Param("personId") UUID personId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
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
