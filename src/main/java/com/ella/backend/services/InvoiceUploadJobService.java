package com.ella.backend.services;

import java.time.LocalDateTime;
import java.security.MessageDigest;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ella.backend.dto.InvoiceUploadResponseDTO;
import com.ella.backend.entities.InvoiceUploadJob;
import com.ella.backend.repositories.InvoiceUploadJobRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceUploadJobService {

    private final InvoiceUploadJobRepository jobRepository;
    private final InvoiceUploadService invoiceUploadService;
    private final ObjectMapper objectMapper;
    private final CacheManager cacheManager;

    @Transactional
    public InvoiceUploadJob createJob(UUID personId,
                                     String filename,
                                     String contentType,
                                     byte[] fileBytes,
                                     String password,
                                     String dueDate) {
        String fileSha256 = computeSha256Hex(fileBytes);

        if (fileSha256 != null) {
            Optional<InvoiceUploadJob> existing = jobRepository
                    .findTopByPersonIdAndFileSha256OrderByCreatedAtDesc(personId, fileSha256);

            if (existing.isPresent()) {
                InvoiceUploadJob existingJob = existing.get();
                // Best-effort idempotency: reuse the most recent job for the same file content,
                // as long as it's not FAILED (FAILED should allow retries).
                if (existingJob.getStatus() != InvoiceUploadJob.Status.FAILED
                        && Objects.equals(existingJob.getPassword(), password)
                        && Objects.equals(existingJob.getDueDate(), dueDate)) {
                    return existingJob;
                }
            }
        }

        InvoiceUploadJob job = new InvoiceUploadJob();
        job.setPersonId(personId);
        job.setStatus(InvoiceUploadJob.Status.PENDING);
        job.setFilename(filename);
        job.setContentType(contentType);
        job.setPassword(password);
        job.setDueDate(dueDate);
        job.setFileSha256(fileSha256);
        job.setFileBytes(fileBytes);
        return jobRepository.save(job);
    }

    public Optional<InvoiceUploadJob> findByIdForPerson(UUID jobId, UUID personId) {
        return jobRepository.findByIdAndPersonId(jobId, personId);
    }

    @Async("invoiceUploadTaskExecutor")
    public void startProcessing(UUID jobId) {
        if (jobId == null) return;
        // We always re-load inside a transaction; avoids detached entity issues.
        processJob(jobId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processJob(UUID jobId) {
        if (jobId == null) return;
        InvoiceUploadJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) {
            log.warn("[InvoiceUploadJob] job not found: {}", jobId);
            return;
        }

        // Idempotency: don't re-run completed jobs.
        if (job.getStatus() == InvoiceUploadJob.Status.SUCCEEDED
                || job.getStatus() == InvoiceUploadJob.Status.FAILED
                || job.getStatus() == InvoiceUploadJob.Status.RUNNING) {
            return;
        }

        job.setStatus(InvoiceUploadJob.Status.RUNNING);
        job.setStartedAt(LocalDateTime.now());
        jobRepository.save(job);

        try {
            InvoiceUploadResponseDTO result = invoiceUploadService.processInvoiceBytesForPerson(
                    job.getPersonId(),
                    job.getFilename(),
                    job.getContentType(),
                    job.getFileBytes(),
                    job.getPassword(),
                    job.getDueDate()
            );

            job.setResultJson(writeResultJson(result));
            job.setStatus(InvoiceUploadJob.Status.SUCCEEDED);
            job.setFinishedAt(LocalDateTime.now());
            jobRepository.save(job);

            // Best-effort cache eviction (processInvoice already evicts, but keep this here
            // to ensure eviction if the processing method changes in the future).
            try {
                var cache = cacheManager.getCache("dashboard");
                if (cache != null) cache.clear();
            } catch (Exception ignored) {}

        } catch (Exception e) {
            log.error("[InvoiceUploadJob] failed jobId={}", jobId, e);
            job.setStatus(InvoiceUploadJob.Status.FAILED);
            job.setErrorMessage(trimError(e.getMessage()));
            job.setFinishedAt(LocalDateTime.now());
            jobRepository.save(job);
        }
    }

    private String writeResultJson(InvoiceUploadResponseDTO result) {
        if (result == null) return null;
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize upload result", e);
        }
    }

    private static String trimError(String message) {
        if (message == null) return null;
        String m = message.trim();
        if (m.length() <= 2000) return m;
        return m.substring(0, 1997) + "...";
    }

    private static String computeSha256Hex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            return toHexLower(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute SHA-256", e);
        }
    }

    private static String toHexLower(byte[] bytes) {
        char[] hex = new char[bytes.length * 2];
        final char[] alphabet = "0123456789abcdef".toCharArray();
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            hex[i * 2] = alphabet[v >>> 4];
            hex[i * 2 + 1] = alphabet[v & 0x0F];
        }
        return new String(hex);
    }
}
