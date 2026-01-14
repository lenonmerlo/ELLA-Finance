package com.ella.backend.services;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.ella.backend.dto.dashboard.InvoiceSummaryDTO;
import com.ella.backend.entities.Invoice;
import com.ella.backend.entities.Person;
import com.ella.backend.enums.InvoiceStatus;
import com.ella.backend.enums.TransactionType;
import com.ella.backend.exceptions.ResourceNotFoundException;
import com.ella.backend.repositories.InstallmentRepository;
import com.ella.backend.repositories.InvoiceRepository;
import com.ella.backend.repositories.PersonRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardInvoicesService {

    private final PersonRepository personRepository;
    private final InvoiceRepository invoiceRepository;
    private final InstallmentRepository installmentRepository;

    public List<InvoiceSummaryDTO> getInvoices(String personId, int year, int month) {
        UUID personUuid = UUID.fromString(personId);
        Person person = personRepository.findById(personUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Person not found"));

        List<Invoice> invoices = invoiceRepository.findByCardOwnerAndMonthAndYear(person, month, year);

        if (log.isInfoEnabled()) {
            List<String> sample = invoices.stream()
                .limit(5)
                .map(inv -> String.format(
                    "%s|%s/%s|due=%s|total(expenseOnly)=%s",
                    inv.getId(),
                    inv.getMonth(),
                    inv.getYear(),
                    inv.getDueDate(),
                    calculateInvoiceExpenseTotal(inv)))
                .toList();
            log.info("[DashboardInvoicesService] personId={} month/year={}/{} invoices={} samples={}",
                personId, month, year, invoices.size(), sample);
        }

        return buildInvoiceSummaries(invoices);
    }

    private List<InvoiceSummaryDTO> buildInvoiceSummaries(List<Invoice> invoices) {
        LocalDate today = LocalDate.now();

        return invoices.stream()
                .map(inv -> {
                    boolean isOverdue = inv.getStatus() != InvoiceStatus.PAID
                            && inv.getDueDate() != null
                            && inv.getDueDate().isBefore(today);

                    boolean isPaid = inv.getStatus() == InvoiceStatus.PAID;

                    String holderName = inv.getCard() != null ? inv.getCard().getCardholderName() : null;
                    if (holderName == null || holderName.isBlank()) {
                        holderName = inv.getCard() != null && inv.getCard().getOwner() != null
                                ? inv.getCard().getOwner().getName()
                                : "";
                    }

                    return InvoiceSummaryDTO.builder()
                        .invoiceId(inv.getId().toString())
                            .creditCardId(inv.getCard().getId().toString())
                            .creditCardName(inv.getCard().getName())
                            .creditCardBrand(inv.getCard().getBrand())
                            .creditCardLastFourDigits(inv.getCard().getLastFourDigits())
                            .personName(holderName)
                            .totalAmount(calculateInvoiceExpenseTotal(inv))
                            .dueDate(inv.getDueDate())
                            .isOverdue(isOverdue)
                        .isPaid(isPaid)
                        .paidDate(inv.getPaidDate())
                            .build();
                })
                .collect(Collectors.toList());
    }

    private BigDecimal calculateInvoiceExpenseTotal(Invoice invoice) {
        if (invoice == null) return BigDecimal.ZERO;
        try {
            return installmentRepository.findByInvoice(invoice).stream()
                    .filter(inst -> inst != null && inst.getTransaction() != null)
                    .filter(inst -> inst.getTransaction().getType() == TransactionType.EXPENSE)
                    .map(inst -> inst.getAmount())
                    .filter(a -> a != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        } catch (Exception e) {
            // fallback para não quebrar o endpoint
            return invoice.getTotalAmount() != null ? invoice.getTotalAmount() : BigDecimal.ZERO;
        }
    }
}
