package com.ella.backend.classification.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ella.backend.classification.entity.CategoryRule;

public interface CategoryRuleRepository extends JpaRepository<CategoryRule, UUID> {
    List<CategoryRule> findByUserIdOrderByPriorityDescCreatedAtDesc(UUID userId);
}
