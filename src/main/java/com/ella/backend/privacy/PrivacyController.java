package com.ella.backend.privacy;

import com.ella.backend.privacy.dto.ConsentRequestDTO;
import com.ella.backend.privacy.dto.ConsentResponseDTO;
import com.ella.backend.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/privacy")
@RequiredArgsConstructor
public class PrivacyController {

    private final PrivacyService privacyService;

    @PostMapping("/consent")
    public ConsentResponseDTO consent(
            @Valid @RequestBody ConsentRequestDTO dto,
            HttpServletRequest request,
            Authentication authentication
    ) {
        CustomUserDetails principal = (CustomUserDetails) authentication.getPrincipal();
        UUID userId = principal.getId();
        String ip = request.getRemoteAddr();

        return privacyService.registerConsent(userId, ip, dto);
    }
}
