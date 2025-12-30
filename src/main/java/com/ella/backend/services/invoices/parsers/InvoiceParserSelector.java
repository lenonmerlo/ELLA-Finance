package com.ella.backend.services.invoices.parsers;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Centralizes parser selection so we can unit-test and avoid regressions.
 */
public final class InvoiceParserSelector {

    private InvoiceParserSelector() {
    }

    public record Candidate(
            InvoiceParserStrategy parser,
            int score,
            boolean applicable,
            LocalDate dueDate,
            int txCount,
            List<TransactionData> transactions
    ) {
    }

    public record Selection(
            Candidate chosen,
            List<Candidate> evaluated
    ) {
    }

    public static Selection selectBest(List<InvoiceParserStrategy> parsers, String text) {
        String t = text == null ? "" : text;

        if (parsers == null || parsers.isEmpty()) {
            throw new IllegalArgumentException("Nenhum parser de fatura está configurado.");
        }

        Candidate best = null;
        List<Candidate> evaluated = new ArrayList<>();

        for (InvoiceParserStrategy parser : parsers) {
            if (parser == null) continue;

            boolean applicable = false;
            try {
                applicable = parser.isApplicable(t);
            } catch (Exception ignored) {
            }

            LocalDate dueDate = null;
            try {
                dueDate = parser.extractDueDate(t);
            } catch (Exception ignored) {
            }

            List<TransactionData> txs = null;
            int txCount = 0;
            try {
                txs = parser.extractTransactions(t);
                txCount = (txs == null ? 0 : txs.size());
            } catch (Exception ignored) {
                txs = null;
                txCount = 0;
            }

            int score = scoreCandidate(applicable, dueDate, txCount);
            Candidate candidate = new Candidate(parser, score, applicable, dueDate, txCount, txs);
            evaluated.add(candidate);

            if (best == null || candidate.score > best.score) {
                best = candidate;
            }
        }

        if (best == null || best.score <= 0) {
            throw new IllegalArgumentException("Layout de fatura não suportado.");
        }

        return new Selection(best, evaluated);
    }

    private static int scoreCandidate(boolean applicable, LocalDate dueDate, int txCount) {
        // Key rule: do NOT let non-applicable parsers win just because they can regex-match generic "dd/MM ... amount" lines.
        // We only trust txCount when the parser is applicable OR can extract a due date.

        int score = 0;

        if (applicable) score += 1_000_000;
        if (dueDate != null) score += 100_000;

        if (txCount > 0 && (applicable || dueDate != null)) {
            score += 10_000;
            score += Math.min(5_000, txCount * 20);
        }

        // If it matched applicability + due date but no transactions, still keep it above zero.
        if (score == 0 && applicable && dueDate != null) {
            score = 1;
        }

        return score;
    }
}
