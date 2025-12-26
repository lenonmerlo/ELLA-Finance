package com.ella.backend.controllers;

import java.util.List;

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
import org.springframework.web.multipart.MultipartFile;

import com.ella.backend.dto.ApiResponse;
import com.ella.backend.dto.InvoiceInsightsDTO;
import com.ella.backend.dto.InvoicePaymentDTO;
import com.ella.backend.dto.InvoiceRequestDTO;
import com.ella.backend.dto.InvoiceResponseDTO;
import com.ella.backend.dto.InvoiceUploadResponseDTO;
import com.ella.backend.services.InvoiceService;
import com.ella.backend.services.InvoiceUploadService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
@Slf4j
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final InvoiceUploadService uploadService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or @securityService.canAccessCard(#dto.cardId)")
    public ResponseEntity<ApiResponse<InvoiceResponseDTO>> create(
            @Valid @RequestBody InvoiceRequestDTO dto
            ) {
        InvoiceResponseDTO created = invoiceService.create(dto);
        return ResponseEntity.status(201).body(ApiResponse.success(created, "Fatura criada com sucesso"));
    }

    @GetMapping("/{id}")
    // @PreAuthorize("hasRole('ADMIN') or @securityService.canAccessInvoice(#id)")
    public ResponseEntity<ApiResponse<InvoiceResponseDTO>> findById(@PathVariable String id) {
        InvoiceResponseDTO found = invoiceService.findById(id);
        return ResponseEntity.ok(ApiResponse.success(found, "Fatura encontrada"));
    }

    @GetMapping
    //@PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<InvoiceResponseDTO>>> findAll() {
        List<InvoiceResponseDTO> list = invoiceService.findAll();
        return ResponseEntity.ok(ApiResponse.success(list, "Faturas encontradas"));
    }

    @GetMapping("/card/{cardId}")
    @PreAuthorize("hasRole('ADMIN') or @securityService.canAccessCard(#cardId)")
    public ResponseEntity<ApiResponse<List<InvoiceResponseDTO>>> findByCard(@PathVariable String cardId) {
        List<InvoiceResponseDTO> list = invoiceService.findyByCard(cardId);
        return ResponseEntity.ok(ApiResponse.success(list, "Faturas do cartão encontradas"));
    }

    @GetMapping("/{invoiceId}/insights")
    @PreAuthorize("hasRole('ADMIN') or @securityService.canAccessInvoice(#invoiceId)")
    public ResponseEntity<ApiResponse<InvoiceInsightsDTO>> getInvoiceInsights(@PathVariable String invoiceId) {
        InvoiceInsightsDTO insights = invoiceService.getInvoiceInsights(java.util.UUID.fromString(invoiceId));
        return ResponseEntity.ok(ApiResponse.success(insights, "Insights da fatura"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @securityService.canAccessInvoice(#id)")
    public ResponseEntity<ApiResponse<InvoiceResponseDTO>> update(@PathVariable String id,
                                                                  @Valid @RequestBody InvoiceRequestDTO dto) {
        InvoiceResponseDTO updated = invoiceService.update(id, dto);
        return ResponseEntity.ok(ApiResponse.success(updated, "Fautra atualizada com sucesso"));
    }

    @PutMapping("/{id}/payment")
    @PreAuthorize("hasRole('ADMIN') or @securityService.canAccessInvoice(#id)")
    public ResponseEntity<ApiResponse<InvoiceResponseDTO>> updatePayment(
            @PathVariable String id,
            @Valid @RequestBody InvoicePaymentDTO dto
    ) {
        InvoiceResponseDTO updated = invoiceService.updatePayment(id, dto);
        return ResponseEntity.ok(ApiResponse.success(updated, "Pagamento da fatura atualizado"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @securityService.canAccessInvoice(#id)")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
        invoiceService.delete(id);
        return ResponseEntity.ok(
                ApiResponse.success(null, "Fatura removida com sucesso")
        );
    }

    /**
     * Upload real de faturas (CSV prioritário; PDF como TODO).
     * Endpoint assumido conforme requisitos da Fase 4.
     * POST /api/invoices/upload
     * multipart/form-data com campo "file".
     * Resposta compatível com o Dashboard atual: { summary, transactions, insights }.
     *
     * TODO:
     * - Implementar parse real de CSV e PDF
     * - Persistir transações derivadas
     * - Gerar insights com base nas transações
     * - Integrar com serviços existentes de dashboard/transactions
     */
    @PostMapping("/upload")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<InvoiceUploadResponseDTO>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "password", required = false) String password,
            @RequestParam(value = "dueDate", required = false) String dueDate) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("Arquivo ausente ou vazio")
            );
        }

        try {
            InvoiceUploadResponseDTO payload = uploadService.processInvoice(file, password, dueDate);
            return ResponseEntity.status(201).body(
                    ApiResponse.success(payload, "Upload processado com sucesso")
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error(e.getMessage())
            );
        } catch (Exception e) {
            log.error("Erro ao processar arquivo no upload de faturas", e);
            return ResponseEntity.status(500).body(
                    ApiResponse.error("Erro interno no servidor: " + e.getMessage())
            );
        }
    }

}
