package com.ella.backend.services;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ella.backend.audit.Auditable;
import com.ella.backend.dto.InvoicePaymentDTO;
import com.ella.backend.dto.InvoiceRequestDTO;
import com.ella.backend.dto.InvoiceResponseDTO;
import com.ella.backend.entities.CreditCard;
import com.ella.backend.entities.Invoice;
import com.ella.backend.enums.InvoiceStatus;
import com.ella.backend.exceptions.ResourceNotFoundException;
import com.ella.backend.repositories.CreditCardRepository;
import com.ella.backend.repositories.FinancialTransactionRepository;
import com.ella.backend.repositories.InstallmentRepository;
import com.ella.backend.repositories.InvoiceRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final CreditCardRepository creditCardRepository;
    private final FinancialTransactionRepository financialTransactionRepository;
    private final InstallmentRepository installmentRepository;

    @Auditable(action = "INVOICE_CREATED", entityType = "Invoice")
    public InvoiceResponseDTO create(InvoiceRequestDTO dto) {
        CreditCard card = creditCardRepository.findById(UUID.fromString(dto.getCardId()))
                .orElseThrow(() -> new ResourceNotFoundException("Cartão não encontrado"));

        Invoice invoice = new Invoice();
        invoice.setCard(card);
        invoice.setMonth(dto.getMonth());
        invoice.setYear(dto.getYear());
        invoice.setDueDate(dto.getDueDate());

        if (dto.getTotalAmount() != null) {
            invoice.setTotalAmount(dto.getTotalAmount());
        }

        if (dto.getPaidAmount() != null) {
            invoice.setPaidAmount(dto.getPaidAmount());
        }

        if (dto.getStatus() != null) {
            invoice.setStatus(dto.getStatus());
        } else {
            invoice.setStatus(InvoiceStatus.OPEN);
        }

        invoice = invoiceRepository.save(invoice);
        return toDTO(invoice);
    }

    public InvoiceResponseDTO findById(String id) {
        Invoice invoice = invoiceRepository.findById(UUID.fromString(id))
                .orElseThrow(() -> new ResourceNotFoundException("Fatura não encontrada"));
        return toDTO(invoice);
    }

    public List<InvoiceResponseDTO> findyByCard(String cardId) {
        CreditCard card = creditCardRepository.findById(UUID.fromString(cardId))
                .orElseThrow(() -> new ResourceNotFoundException("Cartão não encontrado"));

        return invoiceRepository.findByCard(card).stream().map(this::toDTO).toList();
    }

    public List<InvoiceResponseDTO> findAll() {
        return invoiceRepository.findAll().stream().map(this::toDTO).toList();
    }

    @Auditable(action = "INVOICE_UPDATED", entityType = "Invoice")
    public InvoiceResponseDTO update(String id, InvoiceRequestDTO dto) {
        Invoice invoice = invoiceRepository.findById(UUID.fromString(id))
                .orElseThrow(() -> new ResourceNotFoundException("Fatura não encontrada"));

        CreditCard card = creditCardRepository.findById(UUID.fromString(dto.getCardId()))
                .orElseThrow(() -> new ResourceNotFoundException("Cartão não encontrado"));

        invoice.setCard(card);
        invoice.setMonth(dto.getMonth());
        invoice.setYear(dto.getYear());
        invoice.setDueDate(dto.getDueDate());

        if (dto.getTotalAmount() != null) {
            invoice.setTotalAmount(dto.getTotalAmount());
        }

        if (dto.getTotalAmount() != null) {
            invoice.setTotalAmount(dto.getTotalAmount());
        }
        if (dto.getPaidAmount() != null) {
            invoice.setPaidAmount(dto.getPaidAmount());
        }
        if (dto.getStatus() != null) {
            invoice.setStatus(dto.getStatus());
        }

        invoice = invoiceRepository.save(invoice);
        return toDTO(invoice);
    }

    @Auditable(action = "INVOICE_PAYMENT_UPDATED", entityType = "Invoice")
    public InvoiceResponseDTO updatePayment(String id, InvoicePaymentDTO dto) {
        Invoice invoice = invoiceRepository.findById(UUID.fromString(id))
                .orElseThrow(() -> new ResourceNotFoundException("Fatura não encontrada"));

        boolean paid = Boolean.TRUE.equals(dto.getPaid());
        if (paid) {
            invoice.setStatus(InvoiceStatus.PAID);
            invoice.setPaidAmount(invoice.getTotalAmount() != null ? invoice.getTotalAmount() : invoice.getPaidAmount());
            invoice.setPaidDate(dto.getPaidDate());
        } else {
            // volta para aberto; mantém totalAmount
            invoice.setStatus(InvoiceStatus.OPEN);
            invoice.setPaidAmount(java.math.BigDecimal.ZERO);
            invoice.setPaidDate(null);
        }

        invoice = invoiceRepository.save(invoice);
        return toDTO(invoice);
    }

    @Auditable(action = "INVOICE_DELETED", entityType = "Invoice")
    public void delete(String id) {
        Invoice invoice = invoiceRepository.findById(UUID.fromString(id))
                .orElseThrow(() -> new ResourceNotFoundException("Fatura não encontrada"));
        invoiceRepository.delete(invoice);

    }

    private InvoiceResponseDTO toDTO(Invoice invoice) {
        InvoiceResponseDTO dto = new InvoiceResponseDTO();
        dto.setId(invoice.getId().toString());
        dto.setCardId(invoice.getCard().getId().toString());
        dto.setCardName(invoice.getCard().getName());
        dto.setMonth(invoice.getMonth());
        dto.setYear(invoice.getYear());
        dto.setDueDate(invoice.getDueDate());
        dto.setTotalAmount(invoice.getTotalAmount());
        dto.setPaidAmount(invoice.getPaidAmount());
        dto.setPaidDate(invoice.getPaidDate());
        dto.setStatus(invoice.getStatus());
        dto.setCreatedAt(invoice.getCreatedAt());
        dto.setUpdatedAt(invoice.getUpdatedAt());
        // Define a pessoa como o owner do cartão da fatura
        if (invoice.getCard() != null) {
            dto.setPerson(invoice.getCard().getOwner());
        }
        // Popular resumo seguindo a mesma lógica dos personalTransactions
        // Busca todas transações do proprietário do cartão dentro do mês/ano da fatura
        java.util.List<com.ella.backend.dto.TransactionSummaryDTO> resume = new java.util.ArrayList<>();
        var owner = invoice.getCard() != null ? invoice.getCard().getOwner() : null;
        if (owner != null && invoice.getMonth() != null && invoice.getYear() != null) {
            java.time.LocalDate start = java.time.LocalDate.of(invoice.getYear(), invoice.getMonth(), 1);
            java.time.LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
            var txs = financialTransactionRepository.findByPersonAndTransactionDateBetween(owner, start, end);
            for (var t : txs) {
                com.ella.backend.dto.TransactionSummaryDTO ts = new com.ella.backend.dto.TransactionSummaryDTO();
                ts.setId(t.getId().toString());
                ts.setDescription(t.getDescription());
                ts.setAmount(t.getAmount());
                ts.setType(t.getType());
                ts.setCategory(t.getCategory());
                ts.setTransactionDate(t.getTransactionDate());
                resume.add(ts);
            }
        }
        dto.setResume(resume);
        return dto;
    }
}

