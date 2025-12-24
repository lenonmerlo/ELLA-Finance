package com.ella.backend.services;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.ella.backend.dto.FinancialTransactionResponseDTO;
import com.ella.backend.dto.dashboard.TransactionListDTO;
import com.ella.backend.entities.FinancialTransaction;
import com.ella.backend.entities.Person;
import com.ella.backend.exceptions.ResourceNotFoundException;
import com.ella.backend.mappers.FinancialTransactionMapper;
import com.ella.backend.repositories.FinancialTransactionRepository;
import com.ella.backend.repositories.PersonRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardTransactionsService {

    private final PersonRepository personRepository;
    private final FinancialTransactionRepository financialTransactionRepository;

    public TransactionListDTO getTransactions(
            String personId,
            Integer year,
            Integer month,
            LocalDate start,
            LocalDate end,
            String category,
            int page,
            int size
    ) {
        UUID personUuid = UUID.fromString(personId);
        Person person = personRepository.findById(personUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Person not found"));

                LocalDate startDate = start;
                LocalDate endDate = end;

                if (startDate != null && endDate == null) {
                        endDate = startDate;
                } else if (endDate != null && startDate == null) {
                        startDate = endDate;
                }

                if (startDate == null || endDate == null) {
                        int safeYear = year != null ? year : LocalDate.now().getYear();
                        int safeMonth = month != null ? month : LocalDate.now().getMonthValue();
                        YearMonth ym = YearMonth.of(safeYear, safeMonth);
                        startDate = ym.atDay(1);
                        endDate = ym.atEndOfMonth();
                }

        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1),
                Sort.by(Sort.Direction.DESC, "transactionDate"));

        String trimmedCategory = category != null && !category.isBlank() ? category.trim() : null;

        log.info("[DashboardTransactionsService] personId={} startDate={} endDate={} category={} page={} size={}", personId,
                startDate, endDate, trimmedCategory, page, size);

        Page<FinancialTransaction> txPage = trimmedCategory == null
                ? financialTransactionRepository.findByPersonAndTransactionDateBetween(person, startDate, endDate, pageable)
                : financialTransactionRepository.findByPersonAndTransactionDateBetweenAndCategoryIgnoreCase(
                        person, startDate, endDate, trimmedCategory, pageable);

        List<FinancialTransactionResponseDTO> transactions = txPage.getContent().stream()
                .map(FinancialTransactionMapper::toResponseDTO)
                .sorted(Comparator.comparing(FinancialTransactionResponseDTO::transactionDate).reversed())
                .collect(Collectors.toList());

        if (log.isInfoEnabled()) {
            List<String> sample = transactions.stream()
                    .limit(5)
                    .map(tx -> String.format("%s|%s|%s", tx.transactionDate(), tx.type(), tx.amount()))
                    .toList();
            log.info("[DashboardTransactionsService] loaded {} txs; samples={} ", txPage.getNumberOfElements(), sample);
        }

        return TransactionListDTO.builder()
                .transactions(transactions)
                .page(txPage.getNumber())
                .size(txPage.getSize())
                .totalElements(txPage.getTotalElements())
                .totalPages(txPage.getTotalPages())
                .build();
    }
}
