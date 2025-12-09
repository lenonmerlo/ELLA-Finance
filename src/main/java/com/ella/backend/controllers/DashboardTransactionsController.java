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
import com.ella.backend.dto.FinancialTransactionResponseDTO;
import com.ella.backend.dto.dashboard.TransactionListDTO;
import com.ella.backend.services.DashboardTransactionsService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Slf4j
public class DashboardTransactionsController {

    private final DashboardTransactionsService dashboardTransactionsService;

    @GetMapping("/{personId}/transactions")
    @PreAuthorize("hasRole('ADMIN') or @securityService.canAccessPerson(#personId)")
    public ResponseEntity<ApiResponse<TransactionListDTO>> getTransactions(
            @PathVariable String personId,
            @RequestParam(defaultValue = "2025") int year,
            @RequestParam(defaultValue = "1") int month,
            @RequestParam(defaultValue = "50") int limit
    ) {
        log.info("[DashboardTransactions] personId={}, year={}, month={}, limit={}", personId, year, month, limit);
        List<FinancialTransactionResponseDTO> transactions = dashboardTransactionsService.getTransactions(personId, year, month, limit);
        log.info("[DashboardTransactions] loaded {} transactions", transactions.size());
        
        TransactionListDTO response = TransactionListDTO.builder()
                .transactions(transactions)
                .total(transactions.size()) // This is just the count of returned items, not total in DB. For real pagination we need more.
                .page(1)
                .build();

        return ResponseEntity.ok(ApiResponse.success(response, "Transactions loaded successfully"));
    }
}
