package com.ella.backend.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ella.backend.dto.budget.BudgetRequest;
import com.ella.backend.dto.budget.BudgetResponse;
import com.ella.backend.services.BudgetService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/budget")
@RequiredArgsConstructor
public class BudgetController {

    private final BudgetService budgetService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or @securityService.canAccessPerson(#personId)")
    public ResponseEntity<BudgetResponse> createBudget(
            @RequestParam String personId,
            @Valid @RequestBody BudgetRequest request
    ) {
        BudgetResponse response = budgetService.createBudget(personId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{personId}")
    @PreAuthorize("hasRole('ADMIN') or @securityService.canAccessPerson(#personId)")
    public ResponseEntity<BudgetResponse> getBudget(
            @PathVariable String personId
    ) {
        BudgetResponse response = budgetService.getBudget(personId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{budgetId}")
    @PreAuthorize("hasRole('ADMIN') or @securityService.canAccessBudget(#budgetId)")
    public ResponseEntity<BudgetResponse> updateBudget(
            @PathVariable String budgetId,
            @Valid @RequestBody BudgetRequest request
    ) {
        BudgetResponse response = budgetService.updateBudget(budgetId, request);
        return ResponseEntity.ok(response);
    }
}
