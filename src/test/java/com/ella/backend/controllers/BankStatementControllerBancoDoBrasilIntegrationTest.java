package com.ella.backend.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.ella.backend.entities.BankStatement;
import com.ella.backend.entities.BankStatementTransaction;
import com.ella.backend.repositories.BankStatementRepository;
import com.ella.backend.security.CustomUserDetails;
import com.ella.backend.services.bankstatements.extractor.BancoDoBrasilBankStatementExtractorClient;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class BankStatementControllerBancoDoBrasilIntegrationTest {

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
    private MockMvc mockMvc;

    @Autowired
    private BankStatementRepository bankStatementRepository;

    @MockBean
    private BancoDoBrasilBankStatementExtractorClient bancoDoBrasilExtractorClient;

    @BeforeEach
    void cleanup() {
        bankStatementRepository.deleteAll();
    }

    @Test
        @Transactional
    void uploadBbStatement_persistsNormalizedTransactions() throws Exception {
        UUID userId = UUID.randomUUID();
        CustomUserDetails principal = new CustomUserDetails(
                userId,
                "user@test.com",
                "pwd",
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                true
        );

        var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        BancoDoBrasilBankStatementExtractorClient.BancoDoBrasilBankStatementResponse parsed =
                new BancoDoBrasilBankStatementExtractorClient.BancoDoBrasilBankStatementResponse(
                        "BANCO_DO_BRASIL",
                        "2026-01-31",
                        850.00,
                        899.80,
                        List.of(
                                new BancoDoBrasilBankStatementExtractorClient.BancoDoBrasilBankStatementResponse.Tx(
                                        "2026-01-02", "PIX RECEBIDO CLIENTE", -1200.00, null, "CREDIT"
                                ),
                                new BancoDoBrasilBankStatementExtractorClient.BancoDoBrasilBankStatementResponse.Tx(
                                        "2026-01-02", "PAGAMENTO CARTAO", 350.00, null, "DEBIT"
                                ),
                                new BancoDoBrasilBankStatementExtractorClient.BancoDoBrasilBankStatementResponse.Tx(
                                        "2026-01-02", "SALDO DO DIA", 0.00, 850.00, "BALANCE"
                                )
                        ),
                        null
                );

        when(bancoDoBrasilExtractorClient.parseBancoDoBrasilBankStatement(any(byte[].class))).thenReturn(parsed);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "bb-statement.pdf",
                "application/pdf",
                "fake-pdf".getBytes()
        );

        mockMvc.perform(multipart("/api/bank-statements/upload")
                        .file(file)
                        .param("bank", "BB")
                        .with(authentication(auth))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.bank").value("BANCO_DO_BRASIL"))
                .andExpect(jsonPath("$.data.transactions.length()").value(2));

        List<BankStatement> all = bankStatementRepository.findAll();
        assertEquals(1, all.size());

        BankStatement saved = all.get(0);
        assertEquals("BANCO_DO_BRASIL", saved.getBank());
        assertEquals(userId, saved.getUserId());
        assertEquals(0, saved.getOpeningBalance().compareTo(new BigDecimal("850.00")));
        assertEquals(0, saved.getClosingBalance().compareTo(new BigDecimal("899.80")));
        assertNotNull(saved.getStatementDate());

        assertEquals(2, saved.getTransactions().size());
        assertTrue(saved.getTransactions().stream().noneMatch(tx -> tx.getType() == BankStatementTransaction.Type.BALANCE));

        BankStatementTransaction credit = saved.getTransactions().stream()
                .filter(tx -> tx.getType() == BankStatementTransaction.Type.CREDIT)
                .findFirst()
                .orElseThrow();
        assertEquals(0, credit.getAmount().compareTo(new BigDecimal("1200.00")));

        BankStatementTransaction debit = saved.getTransactions().stream()
                .filter(tx -> tx.getType() == BankStatementTransaction.Type.DEBIT)
                .findFirst()
                .orElseThrow();
                assertEquals(0, debit.getAmount().compareTo(new BigDecimal("-350.00")));
    }
}
