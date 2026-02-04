package com.ella.backend.controllers;

import java.util.Objects;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ella.backend.dto.ApiResponse;
import com.ella.backend.dto.reports.GenerateReportRequestDTO;
import com.ella.backend.dto.reports.ReportListItemDTO;
import com.ella.backend.dto.reports.ReportResponseDTO;
import com.ella.backend.services.FinancialReportService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class FinancialReportController {

    private final FinancialReportService financialReportService;

    @PostMapping("/{personId}/generate")
    @PreAuthorize("hasRole('ADMIN') or @securityService.canAccessPerson(#personId)")
    public ResponseEntity<ApiResponse<ReportResponseDTO>> generate(
            @PathVariable String personId,
            @Valid @RequestBody GenerateReportRequestDTO request
    ) {
        ReportResponseDTO report = financialReportService.generate(personId, request);
        return ResponseEntity.ok(ApiResponse.success(report, "Report generated successfully"));
    }

    @GetMapping("/{personId}")
    @PreAuthorize("hasRole('ADMIN') or @securityService.canAccessPerson(#personId)")
    public ResponseEntity<ApiResponse<Page<ReportListItemDTO>>> list(
            @PathVariable String personId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<ReportListItemDTO> reports = financialReportService.list(personId, page, size);
        return ResponseEntity.ok(ApiResponse.success(reports, "Reports loaded successfully"));
    }

    @GetMapping("/{personId}/{reportId}")
    @PreAuthorize("hasRole('ADMIN') or @securityService.canAccessPerson(#personId)")
    public ResponseEntity<ApiResponse<ReportResponseDTO>> get(
            @PathVariable String personId,
            @PathVariable String reportId
    ) {
        ReportResponseDTO report = financialReportService.get(personId, reportId);
        return ResponseEntity.ok(ApiResponse.success(report, "Report loaded successfully"));
    }

    @GetMapping("/{personId}/{reportId}/pdf")
    @PreAuthorize("hasRole('ADMIN') or @securityService.canAccessPerson(#personId)")
    public ResponseEntity<byte[]> downloadPdf(
            @PathVariable String personId,
            @PathVariable String reportId
    ) {
        byte[] pdf = financialReportService.renderPdf(personId, reportId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=relatorio-" + reportId + ".pdf")
            .contentType(Objects.requireNonNull(MediaType.APPLICATION_PDF))
                .body(pdf);
    }
}
