package com.ella.backend.controllers;

import com.ella.backend.dto.ApiResponse;
import com.ella.backend.dto.InvoiceRequestDTO;
import com.ella.backend.dto.InvoiceResponseDTO;
import com.ella.backend.services.InvoiceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;

    @PostMapping
    public ResponseEntity<ApiResponse<InvoiceResponseDTO>> create(
            @Valid @RequestBody InvoiceRequestDTO dto
            ) {
        InvoiceResponseDTO created = invoiceService.create(dto);
        return ResponseEntity.status(201).body(ApiResponse.success(created, "Fatura criada com sucesso"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<InvoiceResponseDTO>> findById(@PathVariable String id) {
        InvoiceResponseDTO found = invoiceService.findById(id);
        return ResponseEntity.ok(ApiResponse.success(found, "Fatura encontrada"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<InvoiceResponseDTO>>> findAll() {
        List<InvoiceResponseDTO> list = invoiceService.findAll();
        return ResponseEntity.ok(ApiResponse.success(list, "Faturas encontradas"));
    }

    @GetMapping("/card/{cardId}")
    public ResponseEntity<ApiResponse<List<InvoiceResponseDTO>>> findByCard(@PathVariable String cardId) {
        List<InvoiceResponseDTO> list = invoiceService.findyByCard(cardId);
        return ResponseEntity.ok(ApiResponse.success(list, "Faturas do cartão encontradas"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<InvoiceResponseDTO>> update(@PathVariable String id,
                                                                  @Valid @RequestBody InvoiceRequestDTO dto) {
        InvoiceResponseDTO updated = invoiceService.update(id, dto);
        return ResponseEntity.ok(ApiResponse.success(updated, "Fautra atualizada com sucesso"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
        invoiceService.delete(id);
        return ResponseEntity.ok(
                ApiResponse.success(null, "Fatura removida com sucesso")
        );
    }

}
