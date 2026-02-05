package com.ella.backend.services.invoices;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ella.backend.entities.FinancialTransaction;
import com.ella.backend.entities.Installment;
import com.ella.backend.entities.Invoice;
import com.ella.backend.repositories.CreditCardRepository;
import com.ella.backend.repositories.FinancialTransactionRepository;
import com.ella.backend.repositories.InstallmentRepository;
import com.ella.backend.repositories.InvoiceRepository;
import com.ella.backend.services.InvoiceService;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceSoftDeleteCascadeTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private CreditCardRepository creditCardRepository;

    @Mock
    private FinancialTransactionRepository financialTransactionRepository;

    @Mock
    private InstallmentRepository installmentRepository;

    @InjectMocks
    private InvoiceService invoiceService;

    @Test
    void delete_softDeletesInvoiceAndLinkedTransactions() {
        UUID invoiceId = UUID.randomUUID();

        Invoice invoice = new Invoice();
        invoice.setId(invoiceId);

        FinancialTransaction tx1 = new FinancialTransaction();
        tx1.setId(UUID.randomUUID());

        FinancialTransaction tx2 = new FinancialTransaction();
        tx2.setId(UUID.randomUUID());

        Installment inst1 = new Installment();
        inst1.setInvoice(invoice);
        inst1.setTransaction(tx1);

        Installment inst2 = new Installment();
        inst2.setInvoice(invoice);
        inst2.setTransaction(tx1); // duplicate tx should be de-duped

        Installment inst3 = new Installment();
        inst3.setInvoice(invoice);
        inst3.setTransaction(tx2);

        when(invoiceRepository.findByIdAndDeletedAtIsNull(invoiceId)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));
        when(installmentRepository.findByInvoice(invoice)).thenReturn(List.of(inst1, inst2, inst3));
        when(financialTransactionRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        invoiceService.delete(invoiceId.toString());

        ArgumentCaptor<Invoice> invoiceCaptor = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceRepository).save(invoiceCaptor.capture());

        Invoice savedInvoice = invoiceCaptor.getValue();
        assertNotNull(savedInvoice.getDeletedAt());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FinancialTransaction>> txCaptor = ArgumentCaptor.forClass(List.class);
        verify(financialTransactionRepository).saveAll(txCaptor.capture());

        List<FinancialTransaction> savedTxs = txCaptor.getValue();
        assertEquals(2, savedTxs.size());
        assertEquals(savedInvoice.getDeletedAt(), savedTxs.get(0).getDeletedAt());
        assertEquals(savedInvoice.getDeletedAt(), savedTxs.get(1).getDeletedAt());
    }
}
