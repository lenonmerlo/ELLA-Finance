package com.ella.backend.repositories;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ella.backend.entities.Subscription;
import com.ella.backend.entities.User;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    Optional<Subscription> findByUser(User user);

    List<Subscription> findByUserIdIn(Collection<UUID> userIds);
}
