package com.ella.backend.controllers;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ella.backend.dto.ApiResponse;
import com.ella.backend.dto.dashboard.GoalListDTO;
import com.ella.backend.dto.dashboard.GoalProgressDTO;
import com.ella.backend.services.DashboardGoalsService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardGoalsController {

    private final DashboardGoalsService dashboardGoalsService;

    @GetMapping("/{personId}/goals")
    @PreAuthorize("hasRole('ADMIN') or @securityService.canAccessPerson(#personId)")
    public ResponseEntity<ApiResponse<GoalListDTO>> getGoals(
            @PathVariable String personId
    ) {
        List<GoalProgressDTO> goals = dashboardGoalsService.getGoals(personId);
        
        GoalListDTO response = GoalListDTO.builder()
                .goals(goals)
                .build();

        return ResponseEntity.ok(ApiResponse.success(response, "Goals loaded successfully"));
    }
}
