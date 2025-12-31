package com.ella.backend.controllers;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ella.backend.dto.ApiResponse;
import com.ella.backend.dto.CriticalTransactionResponseDTO;
import com.ella.backend.dto.CriticalTransactionsStatsDTO;
import com.ella.backend.enums.CriticalReason;
import com.ella.backend.services.CriticalTransactionsService;

@RestController
@RequestMapping("/api/transactions/critical")
public class CriticalTransactionsController {

    private final CriticalTransactionsService service;

    public CriticalTransactionsController(CriticalTransactionsService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or @securityService.canAccessTransactionsOfPerson(#personId)")
    public ResponseEntity<ApiResponse<List<CriticalTransactionResponseDTO>>> listUnreviewed(
            @RequestParam String personId
    ) {
        List<CriticalTransactionResponseDTO> list = service.listUnreviewed(personId);
        return ResponseEntity.ok(ApiResponse.success(list, "Transações críticas pendentes"));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN') or @securityService.canAccessTransactionsOfPerson(#personId)")
    public ResponseEntity<ApiResponse<CriticalTransactionsStatsDTO>> stats(
            @RequestParam String personId
    ) {
        CriticalTransactionsStatsDTO stats = service.stats(personId);
        return ResponseEntity.ok(ApiResponse.success(stats, "Estatísticas de transações críticas"));
    }

    @GetMapping("/by-reason/{reason}")
    @PreAuthorize("hasRole('ADMIN') or @securityService.canAccessTransactionsOfPerson(#personId)")
    public ResponseEntity<ApiResponse<List<CriticalTransactionResponseDTO>>> byReason(
            @RequestParam String personId,
            @PathVariable CriticalReason reason
    ) {
        List<CriticalTransactionResponseDTO> list = service.listUnreviewedByReason(personId, reason);
        return ResponseEntity.ok(ApiResponse.success(list, "Transações críticas por motivo"));
    }

    @PostMapping("/{id}/review")
    @PreAuthorize("hasRole('ADMIN') or @securityService.canAccessTransaction(#id)")
    public ResponseEntity<ApiResponse<CriticalTransactionResponseDTO>> markReviewed(
            @PathVariable String id
    ) {
        CriticalTransactionResponseDTO updated = service.markReviewed(id);
        return ResponseEntity.ok(ApiResponse.success(updated, "Transação marcada como revisada"));
    }
}
