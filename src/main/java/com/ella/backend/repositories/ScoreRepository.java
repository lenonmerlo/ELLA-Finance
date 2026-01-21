package com.ella.backend.repositories;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ella.backend.entities.Score;

public interface ScoreRepository extends JpaRepository<Score, UUID> {

    Optional<Score> findTopByPersonIdOrderByCalculationDateDesc(UUID personId);
}
