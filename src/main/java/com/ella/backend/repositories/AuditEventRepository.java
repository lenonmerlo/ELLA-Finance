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

        @Query("""
                        SELECT a FROM AuditEvent a
                        WHERE (
                                :q IS NULL OR :q = '' OR
                                lower(a.userEmail) LIKE lower(concat('%', cast(:q as string), '%')) OR
                                lower(a.action) LIKE lower(concat('%', cast(:q as string), '%')) OR
                                lower(a.entityType) LIKE lower(concat('%', cast(:q as string), '%')) OR
                                lower(a.entityId) LIKE lower(concat('%', cast(:q as string), '%'))
                        )
                        AND (:action IS NULL OR a.action = :action)
                        AND (:status IS NULL OR a.status = :status)
                        AND (:userId IS NULL OR a.userId = :userId)
                        AND (:userEmail IS NULL OR lower(a.userEmail) = lower(cast(:userEmail as string)))
                        AND (:entityType IS NULL OR a.entityType = :entityType)
                        AND (:entityId IS NULL OR a.entityId = :entityId)
                            AND (cast(:start as timestamp) IS NULL OR a.timestamp >= :start)
                            AND (cast(:end as timestamp) IS NULL OR a.timestamp <= :end)
                        """)
        Page<AuditEvent> search(
                        @Param("q") String q,
                        @Param("action") String action,
                        @Param("status") AuditEventStatus status,
                        @Param("userId") String userId,
                        @Param("userEmail") String userEmail,
                        @Param("entityType") String entityType,
                        @Param("entityId") String entityId,
                        @Param("start") LocalDateTime start,
                        @Param("end") LocalDateTime end,
                        Pageable pageable
        );

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
