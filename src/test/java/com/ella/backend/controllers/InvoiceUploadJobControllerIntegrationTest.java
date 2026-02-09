package com.ella.backend.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.hamcrest.Matchers.nullValue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.ella.backend.dto.InvoiceUploadResponseDTO;
import com.ella.backend.entities.InvoiceUploadJob;
import com.ella.backend.security.SecurityService;
import com.ella.backend.services.InvoiceUploadJobService;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class InvoiceUploadJobControllerIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ella_test")
            .withUsername("ella")
            .withPassword("ella");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.test.database.replace", () -> "NONE");
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");

        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);

        registry.add("jwt.secret", () -> "test-secret");
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    SecurityService securityService;

    @MockBean
    InvoiceUploadJobService jobService;

    @Test
    void uploadAsync_requiresAuthentication() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "invoice.pdf",
                "application/pdf",
                "dummy".getBytes()
        );

        mockMvc.perform(multipart("/api/invoices/upload-async").file(file))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void uploadAsync_emptyFile_returnsBadRequest() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "invoice.pdf",
                "application/pdf",
                new byte[0]
        );

        mockMvc.perform(multipart("/api/invoices/upload-async").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Arquivo ausente ou vazio"));
    }

    @Test
    @WithMockUser
    void uploadAsync_validFile_returnsAcceptedAndJob() throws Exception {
        UUID personId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now();

        when(securityService.getAuthenticatedPersonIdOrThrow()).thenReturn(personId);

        InvoiceUploadJob job = new InvoiceUploadJob();
        job.setId(jobId);
        job.setPersonId(personId);
        job.setStatus(InvoiceUploadJob.Status.PENDING);
        job.setFilename("invoice.pdf");
        job.setContentType("application/pdf");
        job.setFileBytes("dummy".getBytes());
        job.setCreatedAt(createdAt);

        when(jobService.createJob(
                any(UUID.class),
                anyString(),
                anyString(),
                any(byte[].class),
                any(),
                any()
        )).thenReturn(job);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "invoice.pdf",
                "application/pdf",
                "dummy".getBytes()
        );

        mockMvc.perform(multipart("/api/invoices/upload-async")
                        .file(file)
                        .param("dueDate", "2026-02-20"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.jobId").value(jobId.toString()))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.createdAt").exists());

        verify(jobService, times(1)).startProcessing(jobId);
    }

    @Test
    void getJobStatus_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/invoices/upload-jobs/{id}", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getJobStatus_invalidUuid_returnsBadRequest() throws Exception {
        when(securityService.getAuthenticatedPersonIdOrThrow()).thenReturn(UUID.randomUUID());

        mockMvc.perform(get("/api/invoices/upload-jobs/{id}", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Job id inválido"));
    }

    @Test
    @WithMockUser
    void getJobStatus_notFound_returns404() throws Exception {
        UUID personId = UUID.randomUUID();
        when(securityService.getAuthenticatedPersonIdOrThrow()).thenReturn(personId);

        UUID jobId = UUID.randomUUID();
        when(jobService.findByIdForPerson(jobId, personId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/invoices/upload-jobs/{id}", jobId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Job não encontrado"));
    }

    @Test
    @WithMockUser
    void getJobStatus_found_returnsOkWithResultWhenParsable() throws Exception {
        UUID personId = UUID.randomUUID();
        when(securityService.getAuthenticatedPersonIdOrThrow()).thenReturn(personId);

        UUID jobId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now();
        LocalDateTime startedAt = createdAt.plusSeconds(1);
        LocalDateTime finishedAt = createdAt.plusSeconds(2);

        InvoiceUploadResponseDTO result = InvoiceUploadResponseDTO.builder()
                .invoiceId(UUID.randomUUID())
                .totalAmount(new BigDecimal("10.00"))
                .totalTransactions(3)
                .startDate(LocalDate.of(2026, 2, 1))
                .endDate(LocalDate.of(2026, 2, 20))
                .build();

        InvoiceUploadJob job = new InvoiceUploadJob();
        job.setId(jobId);
        job.setPersonId(personId);
        job.setStatus(InvoiceUploadJob.Status.SUCCEEDED);
        job.setFilename("invoice.pdf");
        job.setFileBytes("dummy".getBytes());
        job.setCreatedAt(createdAt);
        job.setStartedAt(startedAt);
        job.setFinishedAt(finishedAt);
        job.setResultJson(objectMapper.writeValueAsString(result));

        when(jobService.findByIdForPerson(jobId, personId)).thenReturn(Optional.of(job));

        mockMvc.perform(get("/api/invoices/upload-jobs/{id}", jobId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.jobId").value(jobId.toString()))
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.resultParseError").value(false))
                .andExpect(jsonPath("$.data.result.invoiceId").value(result.getInvoiceId().toString()))
                .andExpect(jsonPath("$.data.result.totalTransactions").value(3));
    }

        @Test
        @WithMockUser
        void getJobStatus_invalidResultJson_returnsOkWithNullResult() throws Exception {
                UUID personId = UUID.randomUUID();
                when(securityService.getAuthenticatedPersonIdOrThrow()).thenReturn(personId);

                UUID jobId = UUID.randomUUID();
                LocalDateTime createdAt = LocalDateTime.now();

                InvoiceUploadJob job = new InvoiceUploadJob();
                job.setId(jobId);
                job.setPersonId(personId);
                job.setStatus(InvoiceUploadJob.Status.SUCCEEDED);
                job.setFilename("invoice.pdf");
                job.setFileBytes("dummy".getBytes());
                job.setCreatedAt(createdAt);
                job.setResultJson("{not-valid-json");

                when(jobService.findByIdForPerson(jobId, personId)).thenReturn(Optional.of(job));

                mockMvc.perform(get("/api/invoices/upload-jobs/{id}", jobId.toString()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.data.jobId").value(jobId.toString()))
                                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                        .andExpect(jsonPath("$.data.resultParseError").value(true))
                                .andExpect(jsonPath("$.data.result").value(nullValue()));
        }
}
