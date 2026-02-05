package com.ella.backend.controllers.admin;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ella.backend.dto.ApiResponse;
import com.ella.backend.dto.admin.AdminAuditEventListItemDTO;
import com.ella.backend.entities.AuditEvent;
import com.ella.backend.enums.AuditEventStatus;
import com.ella.backend.services.admin.AdminAuditEventService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/audit-events")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
public class AdminAuditEventController {

    private final AdminAuditEventService adminAuditEventService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<AdminAuditEventListItemDTO>>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) AuditEventStatus status,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String userEmail,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<AuditEvent> events = adminAuditEventService.search(
                q,
                action,
                status,
                userId,
                userEmail,
                entityType,
                entityId,
                start,
                end,
                page,
                size
        );

        Page<AdminAuditEventListItemDTO> response = events.map(this::toListItem);

        return ResponseEntity.ok(ApiResponse.success(response, "Eventos de auditoria carregados com sucesso"));
    }

    private AdminAuditEventListItemDTO toListItem(AuditEvent event) {
        AdminAuditEventListItemDTO dto = new AdminAuditEventListItemDTO();
        dto.setId(event.getId() != null ? event.getId().toString() : null);
        dto.setTimestamp(event.getTimestamp());
        dto.setUserId(event.getUserId());
        dto.setUserEmail(event.getUserEmail());
        dto.setIpAddress(event.getIpAddress());
        dto.setAction(event.getAction());
        dto.setEntityId(event.getEntityId());
        dto.setEntityType(event.getEntityType());
        dto.setStatus(event.getStatus());
        dto.setErrorMessage(event.getErrorMessage());
        dto.setDetails(event.getDetails());
        return dto;
    }
}
