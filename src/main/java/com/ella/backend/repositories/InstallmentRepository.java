package com.ella.backend.repositories;

import com.ella.backend.entities.FinancialTransaction;
import com.ella.backend.entities.Installment;
import com.ella.backend.entities.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InstallmentRepository extends JpaRepository<Installment, UUID> {

    List<Installment> findByInvoice(Invoice invoice);

    List<Installment> findByTransaction(FinancialTransaction transaction);
}
