package com.ella.backend.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ella.backend.entities.CreditCard;
import com.ella.backend.entities.Person;

public interface CreditCardRepository extends JpaRepository<CreditCard, UUID> {

    List<CreditCard> findByOwner(Person owner);

    /**
     * Compat com o spec: "userId" aqui representa o owner (Person) que gerencia o cartão.
     * "bankName" mapeia para o campo brand.
     */
    @Query("select c from CreditCard c where c.owner.id = :userId and c.lastFourDigits = :lastFourDigits and lower(c.brand) = lower(:bankName)")
    Optional<CreditCard> findByUserIdAndLastFourDigitsAndBankName(
            @Param("userId") UUID userId,
            @Param("lastFourDigits") String lastFourDigits,
            @Param("bankName") String bankName
    );
}
