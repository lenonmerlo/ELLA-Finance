package com.ella.backend.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.ella.backend.classification.ClassificationService;
import com.ella.backend.classification.dto.ClassificationSuggestResponseDTO;
import com.ella.backend.entities.CreditCard;
import com.ella.backend.entities.FinancialTransaction;
import com.ella.backend.entities.Invoice;
import com.ella.backend.entities.User;
import com.ella.backend.enums.TransactionScope;
import com.ella.backend.enums.TransactionStatus;
import com.ella.backend.enums.TransactionType;
import com.ella.backend.repositories.CreditCardRepository;
import com.ella.backend.repositories.FinancialTransactionRepository;
import com.ella.backend.repositories.InstallmentRepository;
import com.ella.backend.repositories.InvoiceRepository;
import com.ella.backend.services.invoices.extraction.ExtractionPipeline;
import com.ella.backend.services.invoices.extraction.ExtractionResult;
import com.ella.backend.services.invoices.parsers.ParseResult;
import com.ella.backend.services.invoices.parsers.TransactionData;

@ExtendWith(MockitoExtension.class)
class InvoiceUploadServiceDuplicateTransactionsTest {

    @Mock
    private FinancialTransactionRepository transactionRepository;

    @Mock
    private UserService userService;

    @Mock
    private ClassificationService classificationService;

    @Mock
    private CreditCardRepository creditCardRepository;

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private InstallmentRepository installmentRepository;

    @Mock
    private ExtractionPipeline extractionPipeline;

    @InjectMocks
    private InvoiceUploadService invoiceUploadService;

    @BeforeEach
    void setUpAuth() {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("user@example.com", "pw", java.util.List.of())
        );
    }

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void processInvoice_pdf_preservesLegitimateIdenticalTransactions() throws Exception {
        // Given a PDF parse that returns two identical transactions.
        LocalDate dueDate = LocalDate.of(2025, 11, 25);
        LocalDate purchaseDate = LocalDate.of(2025, 11, 10);

        TransactionData tx1 = new TransactionData(
            "COMPRA TESTE",
            new BigDecimal("10.00"),
            TransactionType.EXPENSE,
            "Outros",
            purchaseDate,
            "Sicredi final 2127",
            TransactionScope.PERSONAL
        );
        tx1.installmentNumber = 1;
        tx1.installmentTotal = 3;
        tx1.setDueDate(dueDate);

        TransactionData tx2 = new TransactionData(
            "COMPRA TESTE",
            new BigDecimal("10.00"),
            TransactionType.EXPENSE,
            "Outros",
            purchaseDate,
            "Sicredi final 2127",
            TransactionScope.PERSONAL
        );
        tx2.installmentNumber = 1;
        tx2.installmentTotal = 3;
        tx2.setDueDate(dueDate);

        ParseResult parseResult = ParseResult.builder()
            .bankName("SICREDI")
            .dueDate(dueDate)
            .totalAmount(new BigDecimal("20.00"))
            .transactions(List.of(tx1, tx2))
            .build();

        when(extractionPipeline.extractFromPdfBytes(any(byte[].class), any(), any()))
            .thenReturn(new ExtractionResult(parseResult, "sicredi", "PDFBox", false));

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setName("User");
        user.setEmail("user@example.com");
        when(userService.findByEmail("user@example.com")).thenReturn(user);

        when(classificationService.suggest(eq(user.getId()), any(String.class), any(BigDecimal.class), any(TransactionType.class)))
            .thenReturn(new ClassificationSuggestResponseDTO("Alimentacao", TransactionType.EXPENSE, 0.9, "test"));

        when(creditCardRepository.findByOwner(user)).thenReturn(List.of());
        when(creditCardRepository.save(any(CreditCard.class))).thenAnswer(invocation -> {
            CreditCard cc = invocation.getArgument(0);
            if (cc.getId() == null) cc.setId(UUID.randomUUID());
            return cc;
        });

        when(invoiceRepository.findByCardAndMonthAndYear(any(CreditCard.class), anyInt(), anyInt()))
            .thenReturn(Optional.empty());
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> {
            Invoice inv = invocation.getArgument(0);
            if (inv.getId() == null) inv.setId(UUID.randomUUID());
            if (inv.getTotalAmount() == null) inv.setTotalAmount(BigDecimal.ZERO);
            if (inv.getPaidAmount() == null) inv.setPaidAmount(BigDecimal.ZERO);
            return inv;
        });

        when(installmentRepository.findByTransaction(any(FinancialTransaction.class))).thenReturn(List.of());
        when(installmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        when(transactionRepository.save(any(FinancialTransaction.class))).thenAnswer(invocation -> {
            FinancialTransaction tx = invocation.getArgument(0);
            if (tx.getId() == null) tx.setId(UUID.randomUUID());
            if (tx.getStatus() == null) tx.setStatus(TransactionStatus.PENDING);
            if (tx.getScope() == null) tx.setScope(TransactionScope.PERSONAL);
            return tx;
        });

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "sicredi.pdf",
            "application/pdf",
            new byte[] { 1, 2, 3 }
        );

        // When
        var response = invoiceUploadService.processInvoice(file, null, null);

        // Then: identical rows are preserved as distinct transactions.
        assertEquals(2, response.getTotalTransactions());
        verify(transactionRepository, times(2)).save(any(FinancialTransaction.class));
    }
}
