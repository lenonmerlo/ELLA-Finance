package com.ella.backend.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InvoiceUploadJobStatusResponseDTO {
    private UUID jobId;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String errorMessage;
    private InvoiceUploadResponseDTO result;
    private boolean resultParseError;
}
