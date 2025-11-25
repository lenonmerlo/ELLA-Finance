package com.ella.backend.repositories;

import com.ella.backend.entities.Company;
import com.ella.backend.entities.Person;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CompanyRepository extends JpaRepository<Company, UUID> {

    List<Company> findByOwner(Person owner);
}
