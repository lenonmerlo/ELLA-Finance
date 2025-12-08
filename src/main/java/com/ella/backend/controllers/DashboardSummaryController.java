package com.ella.backend.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ella.backend.dto.ApiResponse;
import com.ella.backend.dto.dashboard.SummaryDTO;
import com.ella.backend.services.DashboardSummaryService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardSummaryController {

    private final DashboardSummaryService dashboardSummaryService;

    @GetMapping("/{personId}/summary")
    @PreAuthorize("hasRole('ADMIN') or @securityService.canAccessPerson(#personId)")
    public ResponseEntity<ApiResponse<SummaryDTO>> getSummary(
            @PathVariable String personId,
            @RequestParam(defaultValue = "2025") int year,
            @RequestParam(defaultValue = "1") int month
    ) {
        SummaryDTO summary = dashboardSummaryService.getSummary(personId, year, month);
        return ResponseEntity.ok(ApiResponse.success(summary, "Summary loaded successfully"));
    }
}
