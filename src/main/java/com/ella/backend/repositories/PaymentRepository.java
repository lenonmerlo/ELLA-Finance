package com.ella.backend.repositories;

import com.ella.backend.entities.Payment;
import com.ella.backend.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> findByUser(User user);
}
