package com.ella.backend.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ella.backend.entities.Goal;
import com.ella.backend.entities.Person;
import com.ella.backend.enums.GoalStatus;

@Repository
public interface GoalRepository extends JpaRepository<Goal, UUID> {

    List<Goal> findByOwner(Person owner);
    
    List<Goal> findByOwnerAndStatusOrderByCreatedAtDesc(Person owner, GoalStatus status);
    
    List<Goal> findByOwnerOrderByCreatedAtDesc(Person owner);
    
    List<Goal> findByOwnerAndCategory(Person owner, String category);
    
    Optional<Goal> findByIdAndOwner(UUID id, Person owner);
}
