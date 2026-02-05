package com.ella.backend.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ella.backend.entities.CreditCard;
import com.ella.backend.entities.Invoice;
import com.ella.backend.entities.Person;
import com.ella.backend.enums.InvoiceStatus;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    Optional<Invoice> findByIdAndDeletedAtIsNull(UUID id);

    List<Invoice> findByDeletedAtIsNull();

    List<Invoice> findByCard(CreditCard card);

    List<Invoice> findByCardAndDeletedAtIsNull(CreditCard card);

    Optional<Invoice> findByCardAndMonthAndYear(CreditCard card, Integer month, Integer year);

    Optional<Invoice> findByCardAndMonthAndYearAndDeletedAtIsNull(CreditCard card, Integer month, Integer year);


    List<Invoice> findByCardAndStatus(CreditCard card, InvoiceStatus status);

    List<Invoice> findByCardAndStatusAndDeletedAtIsNull(CreditCard card, InvoiceStatus status);

    List<Invoice> findByCardOwnerAndMonthAndYear(Person owner, Integer month, Integer year);

    List<Invoice> findByCardOwnerAndMonthAndYearAndDeletedAtIsNull(Person owner, Integer month, Integer year);

    List<Invoice> findByCardOwner(Person owner);

    List<Invoice> findByCardOwnerAndDeletedAtIsNull(Person owner);

    Optional<Invoice> findTopByCardOwnerOrderByYearDescMonthDesc(Person owner);

    Optional<Invoice> findTopByCardOwnerAndDeletedAtIsNullOrderByYearDescMonthDesc(Person owner);

    Optional<Invoice> findTopByCardOwnerOrderByDueDateAsc(Person owner);

    Optional<Invoice> findTopByCardOwnerAndDeletedAtIsNullOrderByDueDateAsc(Person owner);
}
