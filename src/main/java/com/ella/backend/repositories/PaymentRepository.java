package com.ella.backend.repositories;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ella.backend.entities.Payment;
import com.ella.backend.entities.User;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> findByUser(User user);
}
