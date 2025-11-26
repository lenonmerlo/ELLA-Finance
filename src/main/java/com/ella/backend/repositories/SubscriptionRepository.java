package com.ella.backend.repositories;

import com.ella.backend.entities.Subscription;
import com.ella.backend.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    Optional<Subscription> findByUser(User user);
}
