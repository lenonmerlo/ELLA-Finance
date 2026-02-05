package com.ella.backend.controllers.admin;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ella.backend.dto.ApiResponse;
import com.ella.backend.dto.admin.AdminAlertsListItemDTO;
import com.ella.backend.services.admin.AdminAlertsService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/alerts")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
public class AdminAlertsController {

    private final AdminAlertsService adminAlertsService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminAlertsListItemDTO>>> listAlerts(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer limit
    ) {
        List<AdminAlertsListItemDTO> response = adminAlertsService.listAlerts(q, limit);
        return ResponseEntity.ok(ApiResponse.success(response, "Alertas carregados com sucesso"));
    }
}
