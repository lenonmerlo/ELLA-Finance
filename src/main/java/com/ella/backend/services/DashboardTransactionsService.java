package com.ella.backend.services;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.ella.backend.dto.FinancialTransactionResponseDTO;
import com.ella.backend.entities.FinancialTransaction;
import com.ella.backend.entities.Person;
import com.ella.backend.exceptions.ResourceNotFoundException;
import com.ella.backend.mappers.FinancialTransactionMapper;
import com.ella.backend.repositories.FinancialTransactionRepository;
import com.ella.backend.repositories.PersonRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DashboardTransactionsService {

    private final PersonRepository personRepository;
    private final FinancialTransactionRepository financialTransactionRepository;

    public List<FinancialTransactionResponseDTO> getTransactions(String personId, int year, int month, int limit) {
        UUID personUuid = UUID.fromString(personId);
        Person person = personRepository.findById(personUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Person not found"));

        YearMonth ym = YearMonth.of(year, month);
        LocalDate monthStart = ym.atDay(1);
        LocalDate monthEnd = ym.atEndOfMonth();

        // Note: Ideally we should use a repository method with Pageable for limit,
        // but for now I'll fetch and stream limit to match existing logic style, 
        // or better, just fetch all for the month (usually not that many) and limit.
        // If the user wants "transactions" generally (not just month), the query might need adjustment.
        // The user prompt said: GET /api/dashboard/{personId}/transactions?year=2025&month=12&limit=50
        
        List<FinancialTransaction> txs = financialTransactionRepository.findByPersonAndTransactionDateBetween(
                person, monthStart, monthEnd
        );

        return txs.stream()
                .map(FinancialTransactionMapper::toResponseDTO)
                .sorted(Comparator.comparing(FinancialTransactionResponseDTO::transactionDate).reversed())
                .limit(limit > 0 ? limit : Long.MAX_VALUE)
                .collect(Collectors.toList());
    }
}
