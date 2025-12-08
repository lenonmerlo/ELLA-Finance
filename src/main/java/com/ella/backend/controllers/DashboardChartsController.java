package com.ella.backend.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ella.backend.dto.ApiResponse;
import com.ella.backend.dto.dashboard.ChartsDTO;
import com.ella.backend.services.DashboardChartsService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardChartsController {

    private final DashboardChartsService dashboardChartsService;

    @GetMapping("/{personId}/charts")
    @PreAuthorize("hasRole('ADMIN') or @securityService.canAccessPerson(#personId)")
    public ResponseEntity<ApiResponse<ChartsDTO>> getCharts(
            @PathVariable String personId,
            @RequestParam(defaultValue = "2025") int year,
            @RequestParam(required = false) String type
    ) {
        ChartsDTO charts = dashboardChartsService.getCharts(personId, year);
        return ResponseEntity.ok(ApiResponse.success(charts, "Charts loaded successfully"));
    }
}
