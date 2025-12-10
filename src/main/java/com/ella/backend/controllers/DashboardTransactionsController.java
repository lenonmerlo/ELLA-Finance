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
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        log.info("[DashboardTransactions] personId={}, year={}, month={}, start={}, end={}, page={}, size={}",
            personId, year, month, start, end, page, size);

        TransactionListDTO response = dashboardTransactionsService.getTransactions(
            personId,
            year,
            month,
            start != null ? java.time.LocalDate.parse(start) : null,
            end != null ? java.time.LocalDate.parse(end) : null,
            page,
            size
        );

        log.info("[DashboardTransactions] loaded {} transactions (page {}/{})", response.getTransactions().size(),
            response.getPage() + 1, response.getTotalPages());

        return ResponseEntity.ok(ApiResponse.success(response, "Transactions loaded successfully"));
    }
}
