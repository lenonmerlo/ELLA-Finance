package com.ella.backend.repositories;

import com.ella.backend.entities.CreditCard;
import com.ella.backend.entities.Person;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CreditCardRepository extends JpaRepository<CreditCard, UUID> {

    List<CreditCard> findByOwner(Person owner);
}
