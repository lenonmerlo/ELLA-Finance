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
import com.ella.backend.dto.dashboard.InvoiceListDTO;
import com.ella.backend.dto.dashboard.InvoiceSummaryDTO;
import com.ella.backend.services.DashboardInvoicesService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardInvoicesController {

    private final DashboardInvoicesService dashboardInvoicesService;

    @GetMapping("/{personId}/invoices")
    @PreAuthorize("hasRole('ADMIN') or @securityService.canAccessPerson(#personId)")
    public ResponseEntity<ApiResponse<InvoiceListDTO>> getInvoices(
            @PathVariable String personId,
            @RequestParam(defaultValue = "2025") int year,
            @RequestParam(defaultValue = "1") int month
    ) {
        List<InvoiceSummaryDTO> invoices = dashboardInvoicesService.getInvoices(personId, year, month);
        
        InvoiceListDTO response = InvoiceListDTO.builder()
                .invoices(invoices)
                .build();

        return ResponseEntity.ok(ApiResponse.success(response, "Invoices loaded successfully"));
    }
}
