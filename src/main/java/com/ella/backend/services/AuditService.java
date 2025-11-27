package com.ella.backend.services;

import com.ella.backend.entities.AuditEvent;
import com.ella.backend.enums.AuditEventStatus;
import com.ella.backend.repositories.AuditEventRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditEventRepository auditEventRepository;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logEvent(AuditEvent event) {
        try {
            auditEventRepository.save(event);
        } catch (Exception e) {
            log.error("Falha ao salvar evento de auditoria: {}", event, e);
        }
    }

    public AuditEvent createEvent(
            String userId,
            String userEmail,
            String ipAddress,
            String action,
            String entityId,
            String entityType,
            Map<String, Object> details,
            AuditEventStatus status
    ) {
        return AuditEvent.builder()
                .timestamp(LocalDateTime.now())
                .userId(userId)
                .userEmail(userEmail)
                .ipAddress(ipAddress)
                .action(action)
                .entityId(entityId)
                .entityType(entityType)
                .details(details)
                .status(status)
                .build();
    }
    public long countRecentActions(String userId, String action, int minutes) {
        LocalDateTime since = LocalDateTime.now().minusMinutes(minutes);
        return auditEventRepository.countRecentActionByUser(userId, action, since);
    }

    public long countRecentFailedLogins(String userId, int minutes) {
        return countRecentActions(userId, "AUTH_LOGIN_FAILED", minutes);
    }
}
