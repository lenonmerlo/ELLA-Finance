package com.ella.backend.dto.admin;

import java.time.LocalDateTime;
import java.util.Map;

import com.ella.backend.enums.AuditEventStatus;

import lombok.Data;

@Data
public class AdminAuditEventListItemDTO {
    private String id;
    private LocalDateTime timestamp;

    private String userId;
    private String userEmail;
    private String ipAddress;

    private String action;

    private String entityId;
    private String entityType;

    private AuditEventStatus status;
    private String errorMessage;

    private Map<String, Object> details;
}
