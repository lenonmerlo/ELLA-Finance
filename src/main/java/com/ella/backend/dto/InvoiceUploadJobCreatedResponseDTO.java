package com.ella.backend.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InvoiceUploadJobCreatedResponseDTO {
    private UUID jobId;
    private String status;
    private LocalDateTime createdAt;
}
