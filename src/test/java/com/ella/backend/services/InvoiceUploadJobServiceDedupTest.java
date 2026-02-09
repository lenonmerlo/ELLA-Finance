package com.ella.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;

import com.ella.backend.entities.InvoiceUploadJob;
import com.ella.backend.repositories.InvoiceUploadJobRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

@SuppressWarnings("null")
class InvoiceUploadJobServiceDedupTest {

    private final InvoiceUploadJobRepository jobRepository = Mockito.mock(InvoiceUploadJobRepository.class);
    private final InvoiceUploadService invoiceUploadService = Mockito.mock(InvoiceUploadService.class);
    private final ObjectMapper objectMapper = Mockito.mock(ObjectMapper.class);
    private final CacheManager cacheManager = Mockito.mock(CacheManager.class);

    private final InvoiceUploadJobService service = new InvoiceUploadJobService(
            jobRepository,
            invoiceUploadService,
            objectMapper,
            cacheManager
    );

    @Test
    void createJob_reusesExistingSucceededJob_forSameContent() {
        UUID personId = UUID.randomUUID();
        byte[] bytes = "same-content".getBytes(StandardCharsets.UTF_8);
        String sha = sha256Hex(bytes);

        InvoiceUploadJob existing = new InvoiceUploadJob();
        existing.setId(UUID.randomUUID());
        existing.setPersonId(personId);
        existing.setStatus(InvoiceUploadJob.Status.SUCCEEDED);
        existing.setFileSha256(sha);
        existing.setFilename("invoice.pdf");
        existing.setDueDate("2026-02-20");
        existing.setFileBytes(bytes);

        when(jobRepository.findTopByPersonIdAndFileSha256OrderByCreatedAtDesc(personId, sha))
                .thenReturn(Optional.of(existing));

        InvoiceUploadJob result = service.createJob(
                personId,
                "invoice.pdf",
                "application/pdf",
                bytes,
                null,
                "2026-02-20"
        );

        assertThat(result).isSameAs(existing);
        verify(jobRepository, never()).save(Mockito.<InvoiceUploadJob>any());
    }

    @Test
    void createJob_createsNewJob_whenMostRecentIsFailed() {
        UUID personId = UUID.randomUUID();
        byte[] bytes = "same-content".getBytes(StandardCharsets.UTF_8);
        String sha = sha256Hex(bytes);

        InvoiceUploadJob failed = new InvoiceUploadJob();
        failed.setId(UUID.randomUUID());
        failed.setPersonId(personId);
        failed.setStatus(InvoiceUploadJob.Status.FAILED);
        failed.setFileSha256(sha);
        failed.setFilename("invoice.pdf");
        failed.setFileBytes(bytes);

        when(jobRepository.findTopByPersonIdAndFileSha256OrderByCreatedAtDesc(personId, sha))
                .thenReturn(Optional.of(failed));

        InvoiceUploadJob saved = new InvoiceUploadJob();
        saved.setId(UUID.randomUUID());
        saved.setPersonId(personId);
        saved.setStatus(InvoiceUploadJob.Status.PENDING);
        saved.setFileSha256(sha);
        saved.setFilename("invoice.pdf");
        saved.setContentType("application/pdf");
        saved.setFileBytes(bytes);

        when(jobRepository.save(Mockito.<InvoiceUploadJob>any())).thenReturn(saved);

        InvoiceUploadJob result = service.createJob(
                personId,
                "invoice.pdf",
                "application/pdf",
                bytes,
                null,
                "2026-02-20"
        );

        assertThat(result.getId()).isEqualTo(saved.getId());

        ArgumentCaptor<InvoiceUploadJob> captor = ArgumentCaptor.forClass(InvoiceUploadJob.class);
        verify(jobRepository).save(captor.capture());
        InvoiceUploadJob toSave = captor.getValue();

        assertThat(toSave.getPersonId()).isEqualTo(personId);
        assertThat(toSave.getStatus()).isEqualTo(InvoiceUploadJob.Status.PENDING);
        assertThat(toSave.getFileSha256()).isEqualTo(sha);
        assertThat(toSave.getFilename()).isEqualTo("invoice.pdf");
        assertThat(toSave.getContentType()).isEqualTo("application/pdf");
        assertThat(toSave.getFileBytes()).isEqualTo(bytes);
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            char[] out = new char[hash.length * 2];
            final char[] alphabet = "0123456789abcdef".toCharArray();
            for (int i = 0; i < hash.length; i++) {
                int v = hash[i] & 0xFF;
                out[i * 2] = alphabet[v >>> 4];
                out[i * 2 + 1] = alphabet[v & 0x0F];
            }
            return new String(out);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
