// Controller: /api/dashboard
package com.ella.backend.controllers;

import com.ella.backend.dto.ApiResponse;
import com.ella.backend.dto.dashboard.DashboardRequestDTO;
import com.ella.backend.dto.dashboard.DashboardResponseDTO;
import com.ella.backend.services.DashboardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<String>> health() {
        return ResponseEntity.ok(
                ApiResponse.success("Ella Dashboard", "Dashboard service is healthy")
        );
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DashboardResponseDTO>> getDashboard(
            @Valid @RequestBody DashboardRequestDTO request
    ) {
        DashboardResponseDTO dto = dashboardService.buildDashboard(request);

        return ResponseEntity.ok(
                ApiResponse.success(dto, "Dashboard loaded successfully")
        );
    }

    @GetMapping("/quick/{personId}")
    public ResponseEntity<ApiResponse<DashboardResponseDTO>> getQuickDashboard(
            @PathVariable String personId
    ) {
        DashboardResponseDTO dto = dashboardService.buildQuickDashboard(personId);

        return ResponseEntity.ok(
                ApiResponse.success(dto, "Quick dashboard loaded successfully")
        );
    }
}
