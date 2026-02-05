package com.ella.backend.services;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ella.backend.audit.Auditable;
import com.ella.backend.dto.InvoiceInsightsDTO;
import com.ella.backend.dto.InvoicePaymentDTO;
import com.ella.backend.dto.InvoiceRequestDTO;
import com.ella.backend.dto.InvoiceResponseDTO;
import com.ella.backend.entities.CreditCard;
import com.ella.backend.entities.Invoice;
import com.ella.backend.enums.InvoiceStatus;
import com.ella.backend.exceptions.ResourceNotFoundException;
import com.ella.backend.mappers.FinancialTransactionMapper;
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
        Invoice invoice = invoiceRepository.findByIdAndDeletedAtIsNull(UUID.fromString(id))
                .orElseThrow(() -> new ResourceNotFoundException("Fatura não encontrada"));
        return toDTO(invoice);
    }

    public List<InvoiceResponseDTO> findyByCard(String cardId) {
        CreditCard card = creditCardRepository.findById(UUID.fromString(cardId))
                .orElseThrow(() -> new ResourceNotFoundException("Cartão não encontrado"));

        return invoiceRepository.findByCardAndDeletedAtIsNull(card).stream().map(this::toDTO).toList();
    }

    public List<InvoiceResponseDTO> findAll() {
        return invoiceRepository.findByDeletedAtIsNull().stream().map(this::toDTO).toList();
    }

    @Auditable(action = "INVOICE_UPDATED", entityType = "Invoice")
    public InvoiceResponseDTO update(String id, InvoiceRequestDTO dto) {
        Invoice invoice = invoiceRepository.findByIdAndDeletedAtIsNull(UUID.fromString(id))
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
        Invoice invoice = invoiceRepository.findByIdAndDeletedAtIsNull(UUID.fromString(id))
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
        @Transactional
    public void delete(String id) {
        Invoice invoice = invoiceRepository.findByIdAndDeletedAtIsNull(UUID.fromString(id))
                .orElseThrow(() -> new ResourceNotFoundException("Fatura não encontrada"));

        LocalDateTime now = LocalDateTime.now();

        // Soft delete invoice
        invoice.setDeletedAt(now);
        invoiceRepository.save(invoice);

        // Soft delete all transactions linked via installments (cascade)
        var installments = installmentRepository.findByInvoice(invoice);
        var txs = installments.stream()
            .map(inst -> inst != null ? inst.getTransaction() : null)
            .filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.collectingAndThen(
                java.util.stream.Collectors.toMap(
                    tx -> tx.getId(),
                    tx -> tx,
                    (a, b) -> a,
                    java.util.LinkedHashMap::new
                ),
                m -> new java.util.ArrayList<>(m.values())
            ));

        for (var tx : txs) {
            tx.setDeletedAt(now);
        }
        financialTransactionRepository.saveAll(txs);

    }

        public InvoiceInsightsDTO getInvoiceInsights(UUID invoiceId) {
            Invoice invoice = invoiceRepository.findByIdAndDeletedAtIsNull(invoiceId)
            .orElseThrow(() -> new ResourceNotFoundException("Fatura não encontrada"));

        // Transações associadas à fatura via installments
        var installments = installmentRepository.findByInvoice(invoice);
        var transactions = installments.stream()
            .map(inst -> inst != null ? inst.getTransaction() : null)
            .filter(java.util.Objects::nonNull)
            .filter(tx -> tx.getId() != null)
            .collect(java.util.stream.Collectors.collectingAndThen(
                java.util.stream.Collectors.toMap(
                    tx -> tx.getId(),
                    tx -> tx,
                    (a, b) -> a,
                    java.util.LinkedHashMap::new
                ),
                m -> new java.util.ArrayList<>(m.values())
            ));

        // spendingByCategory (apenas despesas)
        java.util.Map<String, java.math.BigDecimal> byCategory = new java.util.HashMap<>();
        for (var tx : transactions) {
            if (tx.getType() != com.ella.backend.enums.TransactionType.EXPENSE) continue;
            String category = tx.getCategory() != null && !tx.getCategory().isBlank() ? tx.getCategory() : "Outros";
            java.math.BigDecimal amount = tx.getAmount() != null ? tx.getAmount() : java.math.BigDecimal.ZERO;
            byCategory.merge(category, amount, java.math.BigDecimal::add);
        }
        java.util.Map<String, Double> spendingByCategory = new java.util.HashMap<>();
        for (var e : byCategory.entrySet()) {
            spendingByCategory.put(e.getKey(), e.getValue() != null ? e.getValue().doubleValue() : 0.0);
        }

        // comparisonWithPreviousMonth
        Double comparison = null;
        if (invoice.getMonth() != null && invoice.getYear() != null && invoice.getCard() != null) {
            int prevMonth = invoice.getMonth() == 1 ? 12 : (invoice.getMonth() - 1);
            int prevYear = invoice.getMonth() == 1 ? (invoice.getYear() - 1) : invoice.getYear();
            var prev = invoiceRepository.findByCardAndMonthAndYearAndDeletedAtIsNull(invoice.getCard(), prevMonth, prevYear).orElse(null);
            if (prev != null && prev.getTotalAmount() != null
                && prev.getTotalAmount().compareTo(java.math.BigDecimal.ZERO) != 0
                && invoice.getTotalAmount() != null) {
            java.math.BigDecimal diff = invoice.getTotalAmount().subtract(prev.getTotalAmount());
            comparison = diff.divide(prev.getTotalAmount(), 6, java.math.RoundingMode.HALF_UP).doubleValue();
            }
        }

        // highestTransaction (maior despesa)
        var highest = transactions.stream()
            .filter(tx -> tx.getType() == com.ella.backend.enums.TransactionType.EXPENSE)
            .filter(tx -> tx.getAmount() != null)
            .max(java.util.Comparator.comparing(com.ella.backend.entities.FinancialTransaction::getAmount))
            .map(FinancialTransactionMapper::toResponseDTO)
            .orElse(null);

        // recurringSubscriptions (MVP por palavras-chave)
        java.util.List<String> subscriptionKeywords = java.util.List.of(
            "NETFLIX",
            "SPOTIFY",
            "ADOBE",
            "APPLE.COM/BILL",
            "APPLE COM BILL",
            "MICROSOFT",
            "GOOGLE",
            "PRIME",
            "AMAZON PRIME",
            "DISNEY",
            "HBO",
            "GLOBOPLAY"
        );

        var recurring = transactions.stream()
            .filter(tx -> tx.getType() == com.ella.backend.enums.TransactionType.EXPENSE)
            .filter(tx -> {
                String desc = tx.getDescription() != null ? tx.getDescription().toUpperCase(java.util.Locale.ROOT) : "";
                for (String kw : subscriptionKeywords) {
                if (desc.contains(kw)) return true;
                }
                return false;
            })
            .sorted(java.util.Comparator.comparing(com.ella.backend.entities.FinancialTransaction::getAmount,
                java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())).reversed())
            .map(FinancialTransactionMapper::toResponseDTO)
            .toList();

        InvoiceInsightsDTO dto = new InvoiceInsightsDTO();
        dto.setSpendingByCategory(spendingByCategory);
        dto.setComparisonWithPreviousMonth(comparison);
        dto.setHighestTransaction(highest);
        dto.setRecurringSubscriptions(recurring);
        return dto;
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
            var txs = financialTransactionRepository.findByPersonAndTransactionDateBetweenAndDeletedAtIsNull(owner, start, end);
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

