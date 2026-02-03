package com.ella.backend.repositories;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.ella.backend.entities.FinancialReport;

public interface FinancialReportRepository extends JpaRepository<FinancialReport, UUID> {
    Page<FinancialReport> findByPersonIdOrderByCreatedAtDesc(UUID personId, Pageable pageable);

    Optional<FinancialReport> findByIdAndPersonId(UUID id, UUID personId);
}
