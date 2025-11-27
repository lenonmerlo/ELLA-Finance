package com.ella.backend.audit;

import com.ella.backend.entities.AuditEvent;
import com.ella.backend.enums.AuditEventStatus;
import com.ella.backend.services.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditService auditService;

    @Around("@annotation(auditable)")
    public Object audit(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        String userId = getCurrentUserId();
        String userEmail = getCurrentUserEmail();
        String ipAddress = getClientIpAddress();
        String action = auditable.action();
        String entityType = auditable.entityType();

        Map<String, Object> details = new HashMap<>();
        Object result = null;
        AuditEventStatus status = AuditEventStatus.SUCCESS;
        String entityId = null;

        try {
            Object[] args = joinPoint.getArgs();
            if (args != null && args.length > 0) {
                details.put("arguments", extractRelevantArguments(args));
            }

            result = joinPoint.proceed();

            entityId = extractEntityId(result);

            // status já começa como SUCCESS, então essa linha é opcional
            // status = AuditEventStatus.SUCCESS;
            details.put("result", "Operation completed successfully");

            return result;
        } catch (Exception e) {
            status = AuditEventStatus.FAILURE;
            details.put("error", e.getMessage());
            details.put("errorType", e.getClass().getSimpleName());
            throw e;
        } finally {
            AuditEvent event = auditService.createEvent(
                    userId,
                    userEmail,
                    ipAddress,
                    action,
                    entityId,
                    entityType,
                    details,
                    status
            );
            auditService.logEvent(event);
        }
    }

    private String getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return "ANONYMOUS";
        }
        return auth.getName();
    }

    private String getCurrentUserEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return auth.getName();
    }

    private String getClientIpAddress() {
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String xForwardedFor = request.getHeader("X-Forwarded-For");
                if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                    return xForwardedFor.split(",")[0].trim();
                }
                return request.getRemoteAddr();
            }
        } catch (Exception e) {
            log.warn("Não foi possível obter o IP do cliente", e);
        }
        return "UNKNOWN";
    }

    private String extractEntityId(Object result) {
        if (result == null) return null;
        try {
            Method getIdMethod = result.getClass().getMethod("getId");
            Object id = getIdMethod.invoke(result);
            return id != null ? id.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> extractRelevantArguments(Object[] args) {
        Map<String, Object> relevant = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            if (arg == null) continue;

            String simpleName = arg.getClass().getSimpleName();
            if ("AuthRequestDTO".equals(simpleName)) {
                // Evita logar senha
                relevant.put("arg" + i + "_type", "AuthRequestDTO");
                continue;
            }

            if (arg instanceof String || arg instanceof Number || arg instanceof Boolean) {
                relevant.put("arg" + i, arg);
            } else {
                relevant.put("arg" + i + "_type", simpleName);
            }
        }
        return relevant;
    }
}
