package com.ella.backend.controllers.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.ella.backend.entities.AuditEvent;
import com.ella.backend.enums.AuditEventStatus;
import com.ella.backend.repositories.AuditEventRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class AdminAuditEventControllerSecurityIntegrationTest {

    @Container
        @SuppressWarnings("resource")
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
    AuditEventRepository auditEventRepository;

    @Test
    void list_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/audit-events"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void list_requiresAdminRole() throws Exception {
        mockMvc.perform(get("/api/admin/audit-events"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void list_asAdmin_returnsOkWithEnvelope() throws Exception {
        AuditEvent event = AuditEvent.builder()
                .timestamp(LocalDateTime.now().minusMinutes(5))
                .userId("admin@ella.local")
                .userEmail("admin@ella.local")
                .ipAddress("127.0.0.1")
                .action("ADMIN_USER_UPDATE_ROLE")
                .entityType("User")
                .entityId("00000000-0000-0000-0000-000000000000")
                .details(Map.of("arg0", "example"))
                .status(AuditEventStatus.SUCCESS)
                .build();
        auditEventRepository.save(Objects.requireNonNull(event));

        mockMvc.perform(get("/api/admin/audit-events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void list_withQuery_filtersResults() throws Exception {
        AuditEvent match = AuditEvent.builder()
                .timestamp(LocalDateTime.now().minusMinutes(4))
                .userId("admin@ella.local")
                .userEmail("admin@ella.local")
                .ipAddress("127.0.0.1")
                .action("ADMIN_USER_UPDATE_STATUS")
                .entityType("User")
                .entityId("11111111-1111-1111-1111-111111111111")
                .details(Map.of())
                .status(AuditEventStatus.SUCCESS)
                .build();
        auditEventRepository.save(Objects.requireNonNull(match));

        AuditEvent other = AuditEvent.builder()
                .timestamp(LocalDateTime.now().minusMinutes(3))
                .userId("admin@ella.local")
                .userEmail("admin@ella.local")
                .ipAddress("127.0.0.1")
                .action("SOME_OTHER_ACTION")
                .entityType("Other")
                .entityId("22222222-2222-2222-2222-222222222222")
                .details(Map.of())
                .status(AuditEventStatus.SUCCESS)
                .build();
        auditEventRepository.save(Objects.requireNonNull(other));

        mockMvc.perform(get("/api/admin/audit-events").param("q", "update_status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].action").value("ADMIN_USER_UPDATE_STATUS"));
    }
}
