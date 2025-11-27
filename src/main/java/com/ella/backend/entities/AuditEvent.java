package com.ella.backend.entities;

import com.ella.backend.enums.AuditEventStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "audit_events", indexes = {
        @Index(name = "idx_audit_user_id", columnList = "user_id"),
        @Index(name = "idx_audit_timestamp", columnList = "timestamp"),
        @Index(name = "idx_audit_action", columnList = "action"),
        @Index(name = "idx_audit_entity_type", columnList = "entity_type")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp;

    @Column(name = "user_id", nullable = false, updatable = false)
    private String userId;

    @Column(name = "user_email", updatable = false)
    private String userEmail;

    @Column(name = "ip_address", updatable = false)
    private String ipAddress;

    @Column(nullable = false, updatable = false)
    private String action;

    @Column(name = "entity_id", updatable = false)
    private String entityId;

    @Column(name = "entity_type", updatable = false)
    private String entityType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", updatable = false)
    private Map<String, Object> details;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private AuditEventStatus status;

    @Column(name = "error_message", updatable = false, length = 1000)
    private String errorMessage;

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }


}
