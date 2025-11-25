package com.ella.backend.repositories;

import com.ella.backend.entities.CreditCard;
import com.ella.backend.entities.Invoice;
import com.ella.backend.entities.Person;
import com.ella.backend.enums.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    List<Invoice> findByCard(CreditCard card);

    Optional<Invoice> findByCardAndMonthAndYear(CreditCard card, Integer month, Integer year);

    List<Invoice> findByCardAndStatus(CreditCard card, InvoiceStatus status);

    List<Invoice> findByCardOwnerAndMonthAndYear(Person owner, Integer month, Integer year);
}
