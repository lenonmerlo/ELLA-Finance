package com.ella.backend.services.admin;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.ella.backend.entities.AuditEvent;
import com.ella.backend.enums.AuditEventStatus;
import com.ella.backend.repositories.AuditEventRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminAuditEventService {

    private final AuditEventRepository auditEventRepository;

    public Page<AuditEvent> search(
            String q,
            String action,
            AuditEventStatus status,
            String userId,
            String userEmail,
            String entityType,
            String entityId,
            LocalDateTime start,
            LocalDateTime end,
            int page,
            int size
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);

        return auditEventRepository.search(
                (q == null || q.isBlank()) ? null : q.trim(),
                (action == null || action.isBlank()) ? null : action.trim(),
                status,
                (userId == null || userId.isBlank()) ? null : userId.trim(),
                (userEmail == null || userEmail.isBlank()) ? null : userEmail.trim(),
                (entityType == null || entityType.isBlank()) ? null : entityType.trim(),
                (entityId == null || entityId.isBlank()) ? null : entityId.trim(),
                start,
                end,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "timestamp"))
        );
    }
}
