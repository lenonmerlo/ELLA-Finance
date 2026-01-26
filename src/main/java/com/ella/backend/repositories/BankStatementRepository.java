package com.ella.backend.repositories;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ella.backend.entities.BankStatement;

public interface BankStatementRepository extends JpaRepository<BankStatement, UUID> {
}
