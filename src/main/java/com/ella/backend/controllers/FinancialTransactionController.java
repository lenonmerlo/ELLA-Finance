package com.ella.backend.controllers;

import com.ella.backend.dto.ApiResponse;
import com.ella.backend.dto.FinancialTransactionRequestDTO;
import com.ella.backend.dto.FinancialTransactionResponseDTO;
import com.ella.backend.services.FinancialTransactionService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/transactions")
public class FinancialTransactionController {

    private final FinancialTransactionService service;

    public FinancialTransactionController(FinancialTransactionService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or @securityService.canAccessPerson(#dto.personId)")
    public ResponseEntity<ApiResponse<FinancialTransactionResponseDTO>> create(
            @Valid @RequestBody FinancialTransactionRequestDTO dto
    ) {
        FinancialTransactionResponseDTO created = service.create(dto);
        return ResponseEntity.ok(ApiResponse.success(created, "Transação criada com sucesso"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @securityService.canAccessTransaction(#id)")
    public ResponseEntity<ApiResponse<FinancialTransactionResponseDTO>> findById(@PathVariable String id) {
        FinancialTransactionResponseDTO dto = service.findById(id);
        return ResponseEntity.ok(ApiResponse.success(dto, "Transação encontrada"));
    }

    @GetMapping("/person/{personId}")
    @PreAuthorize("hasRole('ADMIN') or @securityService.canAccessTransactionsOfPerson(#personId)")
    public ResponseEntity<ApiResponse<List<FinancialTransactionResponseDTO>>> findByPerson(
            @PathVariable String personId
    ) {
        List<FinancialTransactionResponseDTO> list = service.findByPerson(personId);
        return ResponseEntity.ok(ApiResponse.success(list, "Transações encontradas"));
    }

    @GetMapping("/person/{personId}/period")
    @PreAuthorize("hasRole('ADMIN') or @securityService.canAccessTransactionsOfPerson(#personId)")
    public ResponseEntity<ApiResponse<List<FinancialTransactionResponseDTO>>> findByPersonAndPeriod(
            @PathVariable String personId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
    ) {
        List<FinancialTransactionResponseDTO> list = service.findByPersonAndPeriod(personId, start, end);
        return ResponseEntity.ok(ApiResponse.success(list, "Transações no período"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @securityService.canAccessTransaction(#id)")
    public ResponseEntity<ApiResponse<FinancialTransactionResponseDTO>> update(
            @PathVariable String id,
            @Valid @RequestBody FinancialTransactionRequestDTO dto
    ) {
        FinancialTransactionResponseDTO updated = service.update(id, dto);
        return ResponseEntity.ok(ApiResponse.success(updated, "Transação atualizada"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @securityService.canAccessTransaction(#id)")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Transação removida com sucesso"));
    }
}
