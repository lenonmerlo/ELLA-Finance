// Controller: /api/installments
// src/main/java/com/ella/backend/controllers/InstallmentController.java
package com.ella.backend.controllers;

import com.ella.backend.dto.ApiResponse;
import com.ella.backend.dto.InstallmentRequestDTO;
import com.ella.backend.dto.InstallmentResponseDTO;
import com.ella.backend.services.InstallmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/installments")
@RequiredArgsConstructor
public class InstallmentController {

    private final InstallmentService installmentService;

    @PostMapping
    public ResponseEntity<ApiResponse<InstallmentResponseDTO>> create(
            @Valid @RequestBody InstallmentRequestDTO dto
    ) {
        InstallmentResponseDTO created = installmentService.create(dto);
        return ResponseEntity
                .status(201)
                .body(ApiResponse.success(created, "Parcela criada com sucesso"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<InstallmentResponseDTO>> findById(@PathVariable String id) {
        InstallmentResponseDTO found = installmentService.findById(id);
        return ResponseEntity.ok(
                ApiResponse.success(found, "Parcela encontrada")
        );
    }

    @GetMapping("/invoice/{invoiceId}")
    public ResponseEntity<ApiResponse<List<InstallmentResponseDTO>>> findByInvoice(
            @PathVariable String invoiceId
    ) {
        List<InstallmentResponseDTO> list = installmentService.findByInvoice(invoiceId);
        return ResponseEntity.ok(
                ApiResponse.success(list, "Parcelas da fatura encontradas")
        );
    }

    @GetMapping("/transaction/{transactionId}")
    public ResponseEntity<ApiResponse<List<InstallmentResponseDTO>>> findByTransaction(
            @PathVariable String transactionId
    ) {
        List<InstallmentResponseDTO> list = installmentService.findByTransaction(transactionId);
        return ResponseEntity.ok(
                ApiResponse.success(list, "Parcelas da transação encontradas")
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<InstallmentResponseDTO>> update(
            @PathVariable String id,
            @Valid @RequestBody InstallmentRequestDTO dto
    ) {
        InstallmentResponseDTO updated = installmentService.update(id, dto);
        return ResponseEntity.ok(
                ApiResponse.success(updated, "Parcela atualizada com sucesso")
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
        installmentService.delete(id);
        return ResponseEntity.ok(
                ApiResponse.success(null, "Parcela removida com sucesso")
        );
    }
}
