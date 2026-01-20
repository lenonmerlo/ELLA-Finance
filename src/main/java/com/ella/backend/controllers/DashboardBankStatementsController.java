package com.ella.backend.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ella.backend.services.DashboardBankStatementsService;

import lombok.RequiredArgsConstructor;

/**
 * Controller para gerenciar endpoints de Movimentação C/C (Conta Corrente ).
 * Atualmente retorna um placeholder. Será implementado com integrações de extratos bancários.
 */
@RestController
@RequestMapping("/api/dashboard/bank-statements")
@RequiredArgsConstructor
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
    public ResponseEntity<?> getBankStatements(
            @PathVariable Long personId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {

        return ResponseEntity.ok(dashboardBankStatementsService.getBankStatements(personId, year, month));
    }
}
