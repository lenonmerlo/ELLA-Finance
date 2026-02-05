package com.ella.backend.controllers.admin;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ella.backend.dto.ApiResponse;
import com.ella.backend.dto.admin.AdminBillingListItemDTO;
import com.ella.backend.services.admin.AdminBillingService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/billing")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
public class AdminBillingController {

    private final AdminBillingService adminBillingService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<AdminBillingListItemDTO>>> listCustomers(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<AdminBillingListItemDTO> response = adminBillingService.listCustomers(q, page, size);
        return ResponseEntity.ok(ApiResponse.success(response, "Cobrança carregada com sucesso"));
    }
}
