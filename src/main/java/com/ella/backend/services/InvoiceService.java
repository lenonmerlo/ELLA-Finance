package com.ella.backend.services;

import com.ella.backend.dto.InvoiceRequestDTO;
import com.ella.backend.dto.InvoiceResponseDTO;
import com.ella.backend.entities.CreditCard;
import com.ella.backend.entities.Invoice;
import com.ella.backend.enums.InvoiceStatus;
import com.ella.backend.exceptions.ResourceNotFoundException;
import com.ella.backend.repositories.CreditCardRepository;
import com.ella.backend.audit.Auditable;

import com.ella.backend.repositories.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final CreditCardRepository creditCardRepository;

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
        dto.setStatus(invoice.getStatus());
        dto.setCreatedAt(invoice.getCreatedAt());
        dto.setUpdatedAt(invoice.getUpdatedAt());
        return dto;
        }
    }

