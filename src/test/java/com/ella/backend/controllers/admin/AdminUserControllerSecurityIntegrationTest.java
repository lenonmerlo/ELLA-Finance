package com.ella.backend.controllers.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

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

import com.ella.backend.entities.User;
import com.ella.backend.enums.Currency;
import com.ella.backend.enums.Plan;
import com.ella.backend.enums.Role;
import com.ella.backend.repositories.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class AdminUserControllerSecurityIntegrationTest {

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
    UserRepository userRepository;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void list_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void list_requiresAdminRole() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void list_asAdmin_returnsOkWithEnvelope() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    void updateRole_requiresAuthentication() throws Exception {
        mockMvc.perform(put("/api/admin/users/{id}/role", "00000000-0000-0000-0000-000000000000")
                        .contentType("application/json")
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void updateRole_requiresAdminRole() throws Exception {
        mockMvc.perform(put("/api/admin/users/{id}/role", "00000000-0000-0000-0000-000000000000")
                        .contentType("application/json")
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateRole_asAdmin_returnsOk() throws Exception {
        User user = new User();
        user.setName("Teste");
        user.setEmail("teste+role+" + UUID.randomUUID() + "@ella.local");
        user.setPassword("x");
        user.setRole(Role.USER);
        user = userRepository.save(user);

        String body = objectMapper.writeValueAsString(java.util.Map.of("role", "ADMIN"));

        mockMvc.perform(put("/api/admin/users/{id}/role", user.getId().toString())
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.role").value("ADMIN"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updatePlan_asAdmin_returnsOk() throws Exception {
        User user = new User();
        user.setName("Teste");
        user.setEmail("teste+plan+" + UUID.randomUUID() + "@ella.local");
        user.setPassword("x");
        user.setPlan(Plan.FREE);
        user = userRepository.save(user);

        String body = objectMapper.writeValueAsString(java.util.Map.of("plan", "PREMIUM"));

        mockMvc.perform(put("/api/admin/users/{id}/plan", user.getId().toString())
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.plan").value("PREMIUM"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void renewSubscription_asAdmin_returnsOk() throws Exception {
        User user = new User();
        user.setName("Teste");
        user.setEmail("teste+renew+" + UUID.randomUUID() + "@ella.local");
        user.setPassword("x");
        user.setPlan(Plan.PREMIUM);
        user = userRepository.save(user);

        String body = objectMapper.writeValueAsString(java.util.Map.of("days", 30));

        mockMvc.perform(post("/api/admin/users/{id}/subscription/renew", user.getId().toString())
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.plan").value("PREMIUM"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.startDate").exists())
                .andExpect(jsonPath("$.data.endDate").exists());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void renewSubscription_freePlan_returnsBadRequest() throws Exception {
        User user = new User();
        user.setName("Teste");
        user.setEmail("teste+renewfree+" + UUID.randomUUID() + "@ella.local");
        user.setPassword("x");
        user.setPlan(Plan.FREE);
        user = userRepository.save(user);

        String body = objectMapper.writeValueAsString(java.util.Map.of("days", 30));

        mockMvc.perform(post("/api/admin/users/{id}/subscription/renew", user.getId().toString())
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createPayment_asAdmin_returnsOk() throws Exception {
        User user = new User();
        user.setName("Teste");
        user.setEmail("teste+pay+" + UUID.randomUUID() + "@ella.local");
        user.setPassword("x");
        user.setPlan(Plan.PREMIUM);
        user = userRepository.save(user);

        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "plan", "PREMIUM",
                "amount", "29.90",
                "currency", Currency.BRL.name()
        ));

        mockMvc.perform(post("/api/admin/users/{id}/payments", user.getId().toString())
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.plan").value("PREMIUM"))
                .andExpect(jsonPath("$.data.currency").value("BRL"))
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.provider").value("INTERNAL"))
                .andExpect(jsonPath("$.data.paidAt").exists());
    }
}
