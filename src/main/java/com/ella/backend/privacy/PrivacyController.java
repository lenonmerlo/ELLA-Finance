package com.ella.backend.privacy;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ella.backend.privacy.dto.ConsentHistoryDTO;
import com.ella.backend.privacy.dto.ConsentRequestDTO;
import com.ella.backend.privacy.dto.ConsentResponseDTO;
import com.ella.backend.security.CustomUserDetails;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/privacy")
@RequiredArgsConstructor
public class PrivacyController {

    private final PrivacyService privacyService;

    @PostMapping("/consents")
    public ConsentResponseDTO consent(
            @RequestBody ConsentRequestDTO dto,
            HttpServletRequest request,
            Authentication authentication
    ) {
        CustomUserDetails principal = (CustomUserDetails) authentication.getPrincipal();
        UUID userId = principal.getId();
        String ip = resolveClientIp(request);

        return privacyService.registerConsent(userId, ip, dto);
    }

    @GetMapping("/consents")
    public List<ConsentHistoryDTO> getConsents(Authentication authentication) {
        CustomUserDetails principal = (CustomUserDetails) authentication.getPrincipal();
        UUID userId = principal.getId();
        return privacyService.getConsentHistory(userId);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            // pega o primeiro IP da lista de proxies
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

}
