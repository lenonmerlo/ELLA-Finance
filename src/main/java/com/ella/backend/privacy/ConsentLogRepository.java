package com.ella.backend.privacy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ConsentLogRepository extends JpaRepository<ConsentLog, UUID> {
    List<ConsentLog> findByUserIdOrderByAcceptedAtDesc(UUID userId);
}
