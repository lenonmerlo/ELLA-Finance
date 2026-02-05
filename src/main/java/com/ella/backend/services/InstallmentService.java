// src/main/java/com/ella/backend/services/InstallmentService.java
package com.ella.backend.services;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ella.backend.audit.Auditable;
import com.ella.backend.dto.InstallmentRequestDTO;
import com.ella.backend.dto.InstallmentResponseDTO;
import com.ella.backend.entities.FinancialTransaction;
import com.ella.backend.entities.Installment;
import com.ella.backend.entities.Invoice;
import com.ella.backend.exceptions.ResourceNotFoundException;
import com.ella.backend.repositories.FinancialTransactionRepository;
import com.ella.backend.repositories.InstallmentRepository;
import com.ella.backend.repositories.InvoiceRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class InstallmentService {

    private final InstallmentRepository installmentRepository;
    private final InvoiceRepository invoiceRepository;
    private final FinancialTransactionRepository transactionRepository;

    @Auditable(action = "INSTALLMENT_CREATED", entityType = "Installment")
    @Transactional
    public InstallmentResponseDTO create(InstallmentRequestDTO dto) {
        Invoice invoice = invoiceRepository.findByIdAndDeletedAtIsNull(UUID.fromString(dto.getInvoiceId()))
                .orElseThrow(() -> new ResourceNotFoundException("Fatura não encontrada"));

        FinancialTransaction tx = transactionRepository.findByIdAndDeletedAtIsNull(UUID.fromString(dto.getTransactionId()))
                .orElseThrow(() -> new ResourceNotFoundException("Transação não encontrada"));

        Installment installment = new Installment();
        installment.setInvoice(invoice);
        installment.setTransaction(tx);
        installment.setNumber(dto.getNumber());
        installment.setTotal(dto.getTotal());
        installment.setAmount(dto.getAmount());
        installment.setDueDate(dto.getDueDate());

        installment = installmentRepository.save(installment);
        return toDTO(installment);
    }

    public InstallmentResponseDTO findById(String id) {
        Installment installment = installmentRepository.findById(UUID.fromString(id))
                .orElseThrow(() -> new ResourceNotFoundException("Parcela não encontrada"));
        return toDTO(installment);
    }

    public List<InstallmentResponseDTO> findByInvoice(String invoiceId) {
                Invoice invoice = invoiceRepository.findByIdAndDeletedAtIsNull(UUID.fromString(invoiceId))
                .orElseThrow(() -> new ResourceNotFoundException("Fatura não encontrada"));

        return installmentRepository.findByInvoice(invoice).stream()
                .map(this::toDTO)
                .toList();
    }

    public List<InstallmentResponseDTO> findByTransaction(String transactionId) {
                FinancialTransaction tx = transactionRepository.findByIdAndDeletedAtIsNull(UUID.fromString(transactionId))
                .orElseThrow(() -> new ResourceNotFoundException("Transação não encontrada"));

        return installmentRepository.findByTransaction(tx).stream()
                .map(this::toDTO)
                .toList();
    }

    @Auditable(action = "INSTALLMENT_UPDATED", entityType = "Installment")
    @Transactional
    public InstallmentResponseDTO update(String id, InstallmentRequestDTO dto) {
        Installment installment = installmentRepository.findById(UUID.fromString(id))
                .orElseThrow(() -> new ResourceNotFoundException("Parcela não encontrada"));

        Invoice invoice = invoiceRepository.findByIdAndDeletedAtIsNull(UUID.fromString(dto.getInvoiceId()))
                .orElseThrow(() -> new ResourceNotFoundException("Fatura não encontrada"));

        FinancialTransaction tx = transactionRepository.findByIdAndDeletedAtIsNull(UUID.fromString(dto.getTransactionId()))
                .orElseThrow(() -> new ResourceNotFoundException("Transação não encontrada"));

        installment.setInvoice(invoice);
        installment.setTransaction(tx);
        installment.setNumber(dto.getNumber());
        installment.setTotal(dto.getTotal());
        installment.setAmount(dto.getAmount());
        installment.setDueDate(dto.getDueDate());

        installment = installmentRepository.save(installment);
        return toDTO(installment);
    }

    @Auditable(action = "INSTALLMENT_DELETED", entityType = "Installment")
    @Transactional
    public void delete(String id) {
        Installment installment = installmentRepository.findById(UUID.fromString(id))
                .orElseThrow(() -> new ResourceNotFoundException("Parcela não encontrada"));
        installmentRepository.delete(installment);
    }

    private InstallmentResponseDTO toDTO(Installment installment) {
        InstallmentResponseDTO dto = new InstallmentResponseDTO();
        dto.setId(installment.getId().toString());
        dto.setInvoiceId(installment.getInvoice().getId().toString());
        dto.setTransactionId(installment.getTransaction().getId().toString());
        dto.setNumber(installment.getNumber());
        dto.setTotal(installment.getTotal());
        dto.setAmount(installment.getAmount());
        dto.setDueDate(installment.getDueDate());
        dto.setCreatedAt(installment.getCreatedAt());
        dto.setUpdatedAt(installment.getUpdatedAt());
        return dto;
    }
}
