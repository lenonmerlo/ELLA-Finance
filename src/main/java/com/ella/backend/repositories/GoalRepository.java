package com.ella.backend.repositories;

import com.ella.backend.entities.Goal;
import com.ella.backend.entities.Person;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GoalRepository extends JpaRepository<Goal, UUID> {

    List<Goal> findByOwner(Person owner);
}
