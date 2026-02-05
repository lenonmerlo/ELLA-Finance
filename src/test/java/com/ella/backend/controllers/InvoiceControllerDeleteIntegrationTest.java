package com.ella.backend.controllers;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
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

import com.ella.backend.entities.CreditCard;
import com.ella.backend.entities.FinancialTransaction;
import com.ella.backend.entities.Installment;
import com.ella.backend.entities.Invoice;
import com.ella.backend.entities.Person;
import com.ella.backend.enums.TransactionStatus;
import com.ella.backend.enums.TransactionType;
import com.ella.backend.repositories.CreditCardRepository;
import com.ella.backend.repositories.FinancialTransactionRepository;
import com.ella.backend.repositories.InstallmentRepository;
import com.ella.backend.repositories.InvoiceRepository;
import com.ella.backend.repositories.PersonRepository;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class InvoiceControllerDeleteIntegrationTest {

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
    private PersonRepository personRepository;

    @Autowired
    private CreditCardRepository creditCardRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private FinancialTransactionRepository financialTransactionRepository;

    @Autowired
    private InstallmentRepository installmentRepository;

    @Test
        void deleteInvoice_requiresAuthentication() throws Exception {
                mockMvc.perform(delete("/api/invoices/{id}", UUID.randomUUID().toString()))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(roles = "USER")
        void deleteInvoice_requiresAdminRole() throws Exception {
                mockMvc.perform(delete("/api/invoices/{id}", UUID.randomUUID().toString()))
                                .andExpect(status().isForbidden());
        }

        @Test
    @WithMockUser(roles = "ADMIN")
    void deleteInvoice_softDeletesInvoiceAndLinkedTransactions() throws Exception {
        Person person = new Person();
        person.setName("Test User");
        person = personRepository.save(person);

        CreditCard card = new CreditCard();
        card.setName("Cartao Teste");
        card.setBrand("VISA");
        card.setLimitAmount(new BigDecimal("1000.00"));
        card.setClosingDay(10);
        card.setDueDay(20);
        card.setOwner(person);
        card = creditCardRepository.save(card);

        Invoice invoice = new Invoice();
        invoice.setCard(card);
        invoice.setMonth(2);
        invoice.setYear(2026);
        invoice.setDueDate(LocalDate.of(2026, 2, 20));
        invoice.setTotalAmount(new BigDecimal("10.00"));
        invoice.setPaidAmount(BigDecimal.ZERO);
        invoiceRepository.save(Objects.requireNonNull(invoice));
        UUID invoiceId = Objects.requireNonNull(invoice.getId());

        FinancialTransaction tx = FinancialTransaction.builder()
                .person(person)
                .description("Compra teste")
                .amount(new BigDecimal("10.00"))
                .type(TransactionType.EXPENSE)
                .category("Test")
                .transactionDate(LocalDate.of(2026, 2, 1))
                .status(TransactionStatus.PENDING)
                .build();
        financialTransactionRepository.save(Objects.requireNonNull(tx));
        UUID txId = Objects.requireNonNull(tx.getId());

        Installment installment = new Installment();
        installment.setNumber(1);
        installment.setTotal(1);
        installment.setAmount(tx.getAmount());
        installment.setDueDate(invoice.getDueDate());
        installment.setInvoice(invoice);
        installment.setTransaction(tx);
        installmentRepository.save(installment);

        mockMvc.perform(delete("/api/invoices/{id}", invoiceId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Fatura removida com sucesso"));

        Invoice reloadedInvoice = invoiceRepository.findById(invoiceId).orElseThrow();
        FinancialTransaction reloadedTx = financialTransactionRepository.findById(txId).orElseThrow();

        assertNotNull(reloadedInvoice.getDeletedAt());
        assertNotNull(reloadedTx.getDeletedAt());
    }
}
