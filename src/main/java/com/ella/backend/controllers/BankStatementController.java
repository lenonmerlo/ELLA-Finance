package com.ella.backend.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.ella.backend.dto.ApiResponse;
import com.ella.backend.dto.BankStatementUploadResponseDTO;
import com.ella.backend.security.CustomUserDetails;
import com.ella.backend.services.bankstatements.BankStatementUploadService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/bank-statements")
@RequiredArgsConstructor
@Slf4j
public class BankStatementController {

    private final BankStatementUploadService bankStatementUploadService;

    @PostMapping("/upload")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<BankStatementUploadResponseDTO>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "bank", required = false) String bank,
            @RequestParam(value = "password", required = false) String password,
            Authentication authentication
    ) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Arquivo ausente ou vazio"));
        }

        try {
            CustomUserDetails principal = (CustomUserDetails) authentication.getPrincipal();

            String bankNormalized = (bank == null || bank.isBlank()) ? "ITAU_PERSONNALITE" : bank.trim().toUpperCase(java.util.Locale.ROOT);
            var payload = switch (bankNormalized) {
                case "ITAU_PERSONNALITE", "PERSONNALITE", "PERSONALITE" ->
                        bankStatementUploadService.uploadItauPersonnalitePdf(file, principal.getId(), password);
                case "ITAU" -> bankStatementUploadService.uploadItauPdf(file, principal.getId());
                case "C6" -> bankStatementUploadService.uploadC6Pdf(file, principal.getId());
                case "NUBANK", "NU" -> bankStatementUploadService.uploadNubankPdf(file, principal.getId());
                case "BRADESCO", "BRAD" -> bankStatementUploadService.uploadBradescoPdf(file, principal.getId());
                default -> throw new IllegalArgumentException("Banco não suportado para extrato: " + bankNormalized);
            };

            return ResponseEntity.status(201).body(ApiResponse.success(payload, "Extrato enviado com sucesso"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Erro ao processar upload de extrato bancário", e);
            return ResponseEntity.status(500).body(ApiResponse.error("Erro interno no servidor: " + e.getMessage()));
        }
    }
}
