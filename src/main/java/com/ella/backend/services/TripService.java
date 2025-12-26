package com.ella.backend.services;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ella.backend.dto.ApplyTripRequestDTO;
import com.ella.backend.dto.ApplyTripResponseDTO;
import com.ella.backend.entities.FinancialTransaction;
import com.ella.backend.entities.User;
import com.ella.backend.enums.TransactionType;
import com.ella.backend.repositories.FinancialTransactionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TripService {

    private final FinancialTransactionRepository transactionRepository;
    private final UserService userService;

    @Transactional
    @CacheEvict(cacheNames = "dashboard", allEntries = true)
    public ApplyTripResponseDTO applyTrip(ApplyTripRequestDTO request) {
        if (request == null || request.transactionIds() == null || request.transactionIds().isEmpty()) {
            throw new IllegalArgumentException("transactionIds é obrigatório");
        }

        UUID tripId = request.tripId() != null ? request.tripId() : UUID.randomUUID();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) throw new IllegalStateException("Usuário não autenticado");
        User user = userService.findByEmail(auth.getName());

        List<UUID> ids = new ArrayList<>();
        for (String idStr : request.transactionIds()) {
            if (idStr == null || idStr.isBlank()) continue;
            try {
                ids.add(UUID.fromString(idStr.trim()));
            } catch (Exception ignored) {
            }
        }
        if (ids.isEmpty()) throw new IllegalArgumentException("Nenhum transactionId válido");

        List<FinancialTransaction> txs = transactionRepository.findByPersonAndIdIn(user, ids);

        int updated = 0;
        for (FinancialTransaction tx : txs) {
            if (tx == null) continue;
            if (tx.getType() != TransactionType.EXPENSE) continue;
            if (tx.getTripId() != null) continue;

            if (tx.getTripSubcategory() == null || tx.getTripSubcategory().isBlank()) {
                tx.setTripSubcategory(tx.getCategory());
            }
            tx.setTripId(tripId);
            tx.setCategory("Viagem");
            updated++;
        }

        transactionRepository.saveAll(txs);
        return new ApplyTripResponseDTO(tripId, updated);
    }
}
