package com.ella.backend.controllers;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ella.backend.dto.ApiResponse;
import com.ella.backend.dto.dashboard.BankStatementDashboardResponseDTO;
import com.ella.backend.services.DashboardBankStatementsService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Controller para gerenciar endpoints de Movimentação C/C (Conta Corrente ).
 * Atualmente retorna um placeholder. Será implementado com integrações de extratos bancários.
 */
@RestController
@RequestMapping("/api/dashboard/bank-statements")
@RequiredArgsConstructor
@Slf4j
public class DashboardBankStatementsController {

    private final DashboardBankStatementsService dashboardBankStatementsService;

    /**
     * Retorna os extratos bancários do usuário para um período específico.
     *
     * @param personId ID da pessoa
     * @param year Ano (opcional)
     * @param month Mês (opcional)
     * @return ResponseEntity com os dados dos extratos ou placeholder
     */
    @GetMapping("/{personId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<BankStatementDashboardResponseDTO>> getBankStatements(
            @PathVariable String personId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {

        UUID userId = UUID.fromString(personId);
        log.info("[DashboardBankStatements] personId={}, year={}, month={}", userId, year, month);
        BankStatementDashboardResponseDTO payload = dashboardBankStatementsService.getBankStatements(userId, year, month);
        log.info("[DashboardBankStatements] loaded {} transactions", payload != null && payload.getTransactions() != null ? payload.getTransactions().size() : 0);
        return ResponseEntity.ok(ApiResponse.success(payload, "Movimentação C/C"));
    }
}
