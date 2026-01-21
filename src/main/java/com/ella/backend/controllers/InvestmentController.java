package com.ella.backend.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ella.backend.dto.investment.InvestmentRequest;
import com.ella.backend.dto.investment.InvestmentResponse;
import com.ella.backend.dto.investment.InvestmentSummaryResponse;
import com.ella.backend.services.InvestmentService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/investments")
@RequiredArgsConstructor
public class InvestmentController {

    private final InvestmentService investmentService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or @securityService.canAccessPerson(#personId)")
    public ResponseEntity<InvestmentResponse> create(
            @RequestParam String personId,
            @Valid @RequestBody InvestmentRequest request
    ) {
        InvestmentResponse created = investmentService.create(personId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/person/{personId}")
    @PreAuthorize("hasRole('ADMIN') or @securityService.canAccessInvestmentsOfPerson(#personId)")
    public ResponseEntity<InvestmentSummaryResponse> getByPerson(
            @PathVariable String personId
    ) {
        InvestmentSummaryResponse summary = investmentService.getByPerson(personId);
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/{investmentId}")
    @PreAuthorize("hasRole('ADMIN') or @securityService.canAccessInvestment(#investmentId)")
    public ResponseEntity<InvestmentResponse> findById(
            @PathVariable String investmentId
    ) {
        InvestmentResponse found = investmentService.findById(investmentId);
        return ResponseEntity.ok(found);
    }

    @PutMapping("/{investmentId}")
    @PreAuthorize("hasRole('ADMIN') or @securityService.canAccessInvestment(#investmentId)")
    public ResponseEntity<InvestmentResponse> update(
            @PathVariable String investmentId,
            @Valid @RequestBody InvestmentRequest request
    ) {
        InvestmentResponse updated = investmentService.update(investmentId, request);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{investmentId}")
    @PreAuthorize("hasRole('ADMIN') or @securityService.canAccessInvestment(#investmentId)")
    public ResponseEntity<Void> delete(
            @PathVariable String investmentId
    ) {
        investmentService.delete(investmentId);
        return ResponseEntity.noContent().build();
    }
}
