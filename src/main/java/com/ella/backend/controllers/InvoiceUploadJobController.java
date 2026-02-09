package com.ella.backend.controllers;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.ella.backend.dto.ApiResponse;
import com.ella.backend.dto.InvoiceUploadJobCreatedResponseDTO;
import com.ella.backend.dto.InvoiceUploadJobStatusResponseDTO;
import com.ella.backend.dto.InvoiceUploadResponseDTO;
import com.ella.backend.entities.InvoiceUploadJob;
import com.ella.backend.security.SecurityService;
import com.ella.backend.services.InvoiceUploadJobService;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
@Slf4j
public class InvoiceUploadJobController {

    private final InvoiceUploadJobService jobService;
    private final SecurityService securityService;
    private final ObjectMapper objectMapper;

    @PostMapping("/upload-async")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<InvoiceUploadJobCreatedResponseDTO>> uploadAsync(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "password", required = false) String password,
            @RequestParam(value = "dueDate", required = false) String dueDate
    ) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Arquivo ausente ou vazio"));
        }

        try {
            UUID personId = securityService.getAuthenticatedPersonIdOrThrow();

            byte[] bytes = file.getBytes();
            String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload";

            InvoiceUploadJob job = jobService.createJob(
                    personId,
                    filename,
                    file.getContentType(),
                    bytes,
                    password,
                    dueDate
            );

            jobService.startProcessing(job.getId());

            InvoiceUploadJobCreatedResponseDTO payload = InvoiceUploadJobCreatedResponseDTO.builder()
                    .jobId(job.getId())
                    .status(job.getStatus().name())
                    .createdAt(job.getCreatedAt())
                    .build();

            return ResponseEntity.accepted().body(ApiResponse.success(payload, "Upload enfileirado com sucesso"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Erro ao enfileirar upload async", e);
            return ResponseEntity.status(500).body(ApiResponse.error("Erro interno no servidor: " + e.getMessage()));
        }
    }

    @GetMapping("/upload-jobs/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<InvoiceUploadJobStatusResponseDTO>> getJobStatus(@PathVariable String id) {
        UUID personId = securityService.getAuthenticatedPersonIdOrThrow();

        UUID jobId;
        try {
            jobId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Job id inválido"));
        }

        return jobService.findByIdForPerson(jobId, personId)
                .map(job -> ResponseEntity.ok(ApiResponse.success(map(job), "Status do upload")))
                .orElseGet(() -> ResponseEntity.status(404).body(ApiResponse.error("Job não encontrado")));
    }

    private InvoiceUploadJobStatusResponseDTO map(InvoiceUploadJob job) {
        InvoiceUploadResponseDTO result = null;
        boolean resultParseError = false;
        if (job.getResultJson() != null && !job.getResultJson().isBlank()) {
            try {
                result = objectMapper.readValue(job.getResultJson(), InvoiceUploadResponseDTO.class);
            } catch (Exception ignored) {
                // Keep response resilient even if stored JSON cannot be parsed.
                resultParseError = true;
            }
        }

        return InvoiceUploadJobStatusResponseDTO.builder()
                .jobId(job.getId())
                .status(job.getStatus().name())
                .createdAt(job.getCreatedAt())
                .startedAt(job.getStartedAt())
                .finishedAt(job.getFinishedAt())
                .errorMessage(job.getErrorMessage())
                .result(result)
                .resultParseError(resultParseError)
                .build();
    }
}
