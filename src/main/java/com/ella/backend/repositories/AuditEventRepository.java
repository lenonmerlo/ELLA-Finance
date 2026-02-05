package com.ella.backend.repositories;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ella.backend.entities.AuditEvent;
import com.ella.backend.enums.AuditEventStatus;

public interface AuditEventRepository  extends JpaRepository<AuditEvent, UUID> {

    Page<AuditEvent> findByUserId(String userId, Pageable pageable);

    Page<AuditEvent> findByAction(String action, Pageable pageable);

    Page<AuditEvent> findByEntityTypeAndEntityId(String entityType, String entityId, Pageable pageable);

    @Query("SELECT a FROM AuditEvent a WHERE a.timestamp BETWEEN :start AND :end")
    List<AuditEvent> findByTimestampBetween(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
            );

    @Query("SELECT a FROM AuditEvent a WHERE a.userId = :userId AND a.status = :status")
    List<AuditEvent> findByUserIdAndStatus(
            @Param("userId") String userId,
            @Param("status")AuditEventStatus status
            );

    @Query("SELECT COUNT(a) FROM AuditEvent a WHERE a.userId =" +
            " :userId AND a.action =" +
            " :action AND a.timestamp > :since")
    long countRecentActionByUser(
            @Param("userId") String userId,
            @Param("action") String action,
            @Param("since") LocalDateTime since
    );
}
