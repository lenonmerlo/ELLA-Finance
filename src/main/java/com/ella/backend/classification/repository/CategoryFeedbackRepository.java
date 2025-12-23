package com.ella.backend.classification.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ella.backend.classification.entity.CategoryFeedback;

public interface CategoryFeedbackRepository extends JpaRepository<CategoryFeedback, UUID> {}
