package com.ella.backend.repositories;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ella.backend.entities.Insight;
import com.ella.backend.entities.User;

@Repository
public interface InsightRepository extends JpaRepository<Insight, Long> {
    List<Insight> findByUserOrderByGeneratedAtDesc(User user);
    List<Insight> findByUserAndGeneratedAtBetween(User user, LocalDate startDate, LocalDate endDate);
    List<Insight> findByUserAndCategory(User user, String category);
    List<Insight> findByUserAndActionableTrue(User user);
}
