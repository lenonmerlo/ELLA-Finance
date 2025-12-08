package com.ella.backend.controllers;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ella.backend.dto.ApiResponse;
import com.ella.backend.dto.dashboard.InsightDTO;
import com.ella.backend.dto.dashboard.InsightListDTO;
import com.ella.backend.services.DashboardInsightsService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardInsightsController {

    private final DashboardInsightsService dashboardInsightsService;

    @GetMapping("/{personId}/insights")
    @PreAuthorize("hasRole('ADMIN') or @securityService.canAccessPerson(#personId)")
    public ResponseEntity<ApiResponse<InsightListDTO>> getInsights(
            @PathVariable String personId,
            @RequestParam(defaultValue = "2025") int year,
            @RequestParam(defaultValue = "1") int month
    ) {
        List<InsightDTO> insights = dashboardInsightsService.getInsights(personId, year, month);
        
        InsightListDTO response = InsightListDTO.builder()
                .insights(insights)
                .build();

        return ResponseEntity.ok(ApiResponse.success(response, "Insights loaded successfully"));
    }
}
