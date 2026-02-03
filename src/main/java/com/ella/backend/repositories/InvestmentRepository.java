package com.ella.backend.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ella.backend.entities.Investment;
import com.ella.backend.entities.Person;

@Repository
public interface InvestmentRepository extends JpaRepository<Investment, UUID> {
    List<Investment> findByOwner(Person owner);

    List<Investment> findByOwnerAndExcludedFromAssetsFalse(Person owner);

    Optional<Investment> findByIdAndOwner(UUID id, Person owner);
}
