package com.ella.backend.controllers.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
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

import com.ella.backend.entities.Subscription;
import com.ella.backend.entities.User;
import com.ella.backend.enums.Plan;
import com.ella.backend.enums.Status;
import com.ella.backend.enums.SubscriptionStatus;
import com.ella.backend.repositories.SubscriptionRepository;
import com.ella.backend.repositories.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class AdminAlertsControllerSecurityIntegrationTest {

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
    UserRepository userRepository;

    @Autowired
    SubscriptionRepository subscriptionRepository;

    @Test
    void list_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/alerts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void list_requiresAdminRole() throws Exception {
        mockMvc.perform(get("/api/admin/alerts"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void list_asAdmin_returnsItemsWithAlerts() throws Exception {
        User u = new User();
        u.setName("Cliente Alerts");
        u.setEmail("alerts.customer@ella.local");
        u.setPassword("x");
        u.setPlan(Plan.PREMIUM);
        u.setStatus(Status.ACTIVE);
        User saved = userRepository.save(Objects.requireNonNull(u));

        Subscription s = new Subscription();
        s.setUser(saved);
        s.setPlan(Plan.PREMIUM);
        s.setStatus(SubscriptionStatus.ACTIVE);
        s.setStartDate(LocalDate.now().minusDays(25));
        s.setEndDate(LocalDate.now().plusDays(3));
        s.setAutoRenew(false);
        subscriptionRepository.save(Objects.requireNonNull(s));

        mockMvc.perform(get("/api/admin/alerts").param("q", "alerts.customer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()" ).value(1))
                .andExpect(jsonPath("$.data[0].email").value("alerts.customer@ella.local"))
                .andExpect(jsonPath("$.data[0].alerts").isArray())
                .andExpect(jsonPath("$.data[0].alerts.length()" ).value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }
}
