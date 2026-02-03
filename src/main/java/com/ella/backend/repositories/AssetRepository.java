package com.ella.backend.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ella.backend.entities.Asset;
import com.ella.backend.entities.Person;

@Repository
public interface AssetRepository extends JpaRepository<Asset, UUID> {
    List<Asset> findByOwner(Person owner);

    Optional<Asset> findByIdAndOwner(UUID id, Person owner);

    Optional<Asset> findByInvestmentId(UUID investmentId);

    void deleteByInvestmentId(UUID investmentId);
}
