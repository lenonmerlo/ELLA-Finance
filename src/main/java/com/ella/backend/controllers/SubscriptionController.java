// Rotas: /api/subscriptions/**
package com.ella.backend.controllers;

import com.ella.backend.dto.payment.SubscriptionResponseDTO;
import com.ella.backend.services.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasRole('ADMIN') or @securityService.isCurrentUser(#userId)")
    public ResponseEntity<SubscriptionResponseDTO> getByUser(@PathVariable String userId) {
        SubscriptionResponseDTO dto = subscriptionService.getByUserId(userId);
        return ResponseEntity.ok(dto);
    }
}
