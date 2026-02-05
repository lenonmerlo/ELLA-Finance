package com.ella.backend.controllers.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

import com.ella.backend.entities.Payment;
import com.ella.backend.entities.Subscription;
import com.ella.backend.entities.User;
import com.ella.backend.enums.Currency;
import com.ella.backend.enums.PaymentProvider;
import com.ella.backend.enums.PaymentStatus;
import com.ella.backend.enums.Plan;
import com.ella.backend.enums.Status;
import com.ella.backend.enums.SubscriptionStatus;
import com.ella.backend.repositories.PaymentRepository;
import com.ella.backend.repositories.SubscriptionRepository;
import com.ella.backend.repositories.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class AdminBillingControllerSecurityIntegrationTest {

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

    @Autowired
    PaymentRepository paymentRepository;

    @Test
    void list_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/billing"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void list_requiresAdminRole() throws Exception {
        mockMvc.perform(get("/api/admin/billing"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void list_asAdmin_returnsOkWithEnvelope() throws Exception {
        User u = new User();
        u.setName("Cliente Billing");
        u.setEmail("billing.customer@ella.local");
        u.setPassword("x");
        u.setPlan(Plan.PREMIUM);
        u.setStatus(Status.ACTIVE);
        User saved = userRepository.save(Objects.requireNonNull(u));

        Subscription s = new Subscription();
        s.setUser(saved);
        s.setPlan(Plan.PREMIUM);
        s.setStatus(SubscriptionStatus.ACTIVE);
        s.setStartDate(LocalDate.now().minusDays(1));
        s.setEndDate(LocalDate.now().plusDays(10));
        s.setAutoRenew(false);
        subscriptionRepository.save(Objects.requireNonNull(s));

        Payment p = new Payment();
        p.setUser(saved);
        p.setPlan(Plan.PREMIUM);
        p.setAmount(new BigDecimal("29.90"));
        p.setCurrency(Currency.BRL);
        p.setStatus(PaymentStatus.APPROVED);
        p.setProvider(PaymentProvider.INTERNAL);
        p.setProviderPaymentId("SIMULATED-TEST");
        p.setProviderRawStatus("approved");
        p.setCreatedAt(LocalDateTime.now().minusMinutes(5));
        p.setPaidAt(LocalDateTime.now().minusMinutes(4));
        paymentRepository.save(Objects.requireNonNull(p));

        mockMvc.perform(get("/api/admin/billing").param("q", "billing.customer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].email").value("billing.customer@ella.local"))
                .andExpect(jsonPath("$.data.content[0].billingStatus").value("UP_TO_DATE"))
                .andExpect(jsonPath("$.data.content[0].lastPaidAt").exists());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void list_showsOverdue_whenSubscriptionExpired() throws Exception {
        User u = new User();
        u.setName("Cliente Atrasado");
        u.setEmail("overdue.customer@ella.local");
        u.setPassword("x");
        u.setPlan(Plan.ESSENTIAL);
        u.setStatus(Status.ACTIVE);
        User saved = userRepository.save(Objects.requireNonNull(u));

        Subscription s = new Subscription();
        s.setUser(saved);
        s.setPlan(Plan.ESSENTIAL);
        s.setStatus(SubscriptionStatus.ACTIVE);
        s.setStartDate(LocalDate.now().minusDays(40));
        s.setEndDate(LocalDate.now().minusDays(1));
        s.setAutoRenew(false);
        subscriptionRepository.save(Objects.requireNonNull(s));

        mockMvc.perform(get("/api/admin/billing").param("q", "overdue.customer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].billingStatus").value("OVERDUE"));
    }
}
