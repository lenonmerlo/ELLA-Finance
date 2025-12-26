package com.ella.backend.services;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ella.backend.dto.TripSuggestionDTO;
import com.ella.backend.entities.FinancialTransaction;
import com.ella.backend.enums.TransactionType;

@Service
public class TripDetectionService {

    private static final int MIN_CANDIDATES = 4;
    private static final int MAX_WINDOW_DAYS = 30;

    private static final Set<String> TRAVEL_KEYWORDS = Set.of(
            "AIRBNB",
            "BOOKING",
            "EXPEDIA",
            "TRIVAGO",
            "HOTEL",
            "HOTEIS",
            "HOTÉIS",
            "LATAM",
            "UNITED",
            "AZUL",
            "GOL",
            "TICKETE",
            "TICKET",
            "TRAVEL",
            "CRUISE",
            "CRUISES",
            "DUBAI",
            "NEW YORK",
            "NY"
    );

    public Optional<TripSuggestionDTO> detect(List<FinancialTransaction> transactions) {
        if (transactions == null || transactions.isEmpty()) return Optional.empty();

        List<FinancialTransaction> expenses = transactions.stream()
                .filter(t -> t != null && t.getId() != null)
                .filter(t -> t.getType() == TransactionType.EXPENSE)
                .toList();

        if (expenses.size() < MIN_CANDIDATES) return Optional.empty();

        List<FinancialTransaction> candidates = expenses.stream()
                .filter(this::isTravelCandidate)
                .toList();

        if (candidates.size() < MIN_CANDIDATES) return Optional.empty();

        List<FinancialTransaction> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator.comparing(this::getBestDate));

        int bestStart = -1;
        int bestEnd = -1;

        int j = 0;
        for (int i = 0; i < sorted.size(); i++) {
            while (j < sorted.size()) {
                LocalDate di = getBestDate(sorted.get(i));
                LocalDate dj = getBestDate(sorted.get(j));
                if (di == null || dj == null) break;
                if (di.plusDays(MAX_WINDOW_DAYS).isBefore(dj)) break;
                j++;
            }
            int windowSize = j - i;
            if (windowSize >= MIN_CANDIDATES) {
                if (bestStart == -1 || windowSize > (bestEnd - bestStart + 1)) {
                    bestStart = i;
                    bestEnd = j - 1;
                }
            }
        }

        if (bestStart == -1) return Optional.empty();

        LocalDate start = getBestDate(sorted.get(bestStart));
        LocalDate end = getBestDate(sorted.get(bestEnd));
        if (start == null || end == null) return Optional.empty();

        // Para aplicar, inclui TODAS as despesas do upload dentro do intervalo detectado.
        List<String> idsToApply = expenses.stream()
                .filter(t -> {
                    LocalDate d = getBestDate(t);
                    return d != null && !d.isBefore(start) && !d.isAfter(end);
                })
                .map(t -> t.getId().toString())
                .toList();

        if (idsToApply.size() < MIN_CANDIDATES) return Optional.empty();

        UUID tripId = UUID.randomUUID();
        String msg = "Identificamos uma possível viagem entre "
                + start.format(DateTimeFormatter.ofPattern("dd/MM"))
                + " e "
                + end.format(DateTimeFormatter.ofPattern("dd/MM"))
                + ". Deseja agrupar como 'Viagem' mantendo a categoria original como subcategoria?";

        return Optional.of(new TripSuggestionDTO(tripId, start, end, idsToApply, msg));
    }

    private boolean isTravelCandidate(FinancialTransaction tx) {
        String category = tx.getCategory() == null ? "" : tx.getCategory().trim();
        if (equalsIgnoreCase(category, "Viagem") || equalsIgnoreCase(category, "Hospedagem")) {
            return true;
        }
        String n = normalize(tx.getDescription());
        for (String kw : TRAVEL_KEYWORDS) {
            if (n.contains(kw)) return true;
        }
        return false;
    }

    private LocalDate getBestDate(FinancialTransaction tx) {
        if (tx == null) return null;
        // compra (linha original) é melhor para agrupar; fallback para transactionDate
        return tx.getPurchaseDate() != null ? tx.getPurchaseDate() : tx.getTransactionDate();
    }

    private boolean equalsIgnoreCase(String a, String b) {
        if (a == null || b == null) return false;
        return a.equalsIgnoreCase(b);
    }

    private String normalize(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return n.toUpperCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }
}
