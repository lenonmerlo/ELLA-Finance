package com.ella.backend.repositories;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ella.backend.entities.Goal;
import com.ella.backend.entities.Person;
import com.ella.backend.enums.GoalStatus;

public interface GoalRepository extends JpaRepository<Goal, UUID> {

    List<Goal> findByOwner(Person owner);

    boolean existsByOwner(Person owner);

    long countByOwnerAndStatus(Person owner, GoalStatus status);
}
