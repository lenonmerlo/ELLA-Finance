package com.ella.backend.services.bankstatements.parsers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ella.backend.entities.BankStatementTransaction;

import lombok.Getter;

public class ItauBankStatementParser {

    private static final boolean DEBUG = isDebugEnabled();

    private static boolean isDebugEnabled() {
        String fromProp = System.getProperty("ELLA_PARSER_DEBUG");
        if (fromProp != null) {
            return Boolean.parseBoolean(fromProp);
        }
        String fromEnv = System.getenv("ELLA_PARSER_DEBUG");
        return Boolean.parseBoolean(fromEnv != null ? fromEnv : "false");
    }

    private static final Locale LOCALE_PT_BR = new Locale("pt", "BR");

    private static final Pattern PERIOD_PATTERN = Pattern.compile(
            "(?i)\\bper[ií]odo\\b[^0-9]*(\\d{2}/\\d{2}/\\d{4})\\s*(?:a|\\-|até)\\s*(\\d{2}/\\d{2}/\\d{4})");
    private static final Pattern DATE_FULL_PATTERN = Pattern.compile("\\b(\\d{2}/\\d{2}/\\d{4})\\b");

    private static final Pattern OPENING_PATTERN = Pattern.compile(
            "(?i)\\b(saldo\\s*(?:anterior|inicial))\\b[^0-9\\-]*(-?\\d{1,3}(?:\\.\\d{3})*,\\d{2})");
    private static final Pattern CLOSING_PATTERN = Pattern.compile(
            "(?i)\\b(saldo\\s*(?:final|atual))\\b[^0-9\\-]*(-?\\d{1,3}(?:\\.\\d{3})*,\\d{2})");
    private static final Pattern CREDIT_LIMIT_PATTERN = Pattern.compile(
            "(?i)\\b(limite\\s*(?:total|de\\s*cr[eé]dito|cr[eé]dito))\\b[^0-9\\-]*(-?\\d{1,3}(?:\\.\\d{3})*,\\d{2})");
    private static final Pattern AVAILABLE_LIMIT_PATTERN = Pattern.compile(
            "(?i)\\b(limite\\s*(?:dispon[ií]vel|utiliz[aá]vel|disponivel))\\b[^0-9\\-]*(-?\\d{1,3}(?:\\.\\d{3})*,\\d{2})");

        // Common line formats:
        //  dd/MM[/yyyy]  <description...>  <amount>  <balance>
        //  dd/MM[/yyyy]  <description...>  <balance>            (balance-only lines like SALDO ...)
        //  dd/MM[/yyyy]  <description...>  <amount>
        private static final Pattern TX_WITH_BALANCE = Pattern.compile(
            "^\\s*(\\d{2}/\\d{2})(?:/(\\d{4}))?\\s+(.+?)\\s+(-?\\d{1,3}(?:\\.\\d{3})*,\\d{2})(?:\\s*([DC]))?\\s+(-?\\d{1,3}(?:\\.\\d{3})*,\\d{2})\\s*$");
        private static final Pattern BALANCE_ONLY = Pattern.compile(
            "^\\s*(\\d{2}/\\d{2})(?:/(\\d{4}))?\\s+(.+?)\\s+(-?\\d{1,3}(?:\\.\\d{3})*,\\d{2})\\s*$");
    private static final Pattern TX_NO_BALANCE = Pattern.compile(
            "^\\s*(\\d{2}/\\d{2})(?:/(\\d{4}))?\\s+(.+?)\\s+(-?\\d{1,3}(?:\\.\\d{3})*,\\d{2})(?:\\s*([DC]))?\\s*$");

    // Used to split transaction entries when PDF text extraction collapses newlines.
    // IMPORTANT: require start-of-string OR whitespace right before the date to avoid
    // splitting on embedded references like "D01/12" inside descriptions.
    private static final Pattern ENTRY_DATE_TOKEN = Pattern.compile("(?:(?<=^)|(?<=\\s))(\\d{2}/\\d{2}(?:/\\d{4})?)\\s+");

    /**
     * Extrai apenas a seção "lançamentos" do texto do PDF.
     * Ignora "lançamentos futuros"/"saídas futuras" e outras seções.
     */
    private static String extractLancamentosSection(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return "";
        }

        // Keep indices stable (avoid aggressive normalization here).
        String text = rawText.replace('\u00A0', ' ');

        Pattern lancamentosStart = Pattern.compile(
                "(?i)\\blan[cç]amentos\\b[^a-zA-Z]{0,80}per[ií]odo\\s+de\\s+visualiza[cç][aã]o");
        // IMPORTANT: do NOT include "limites" or "juros" here.
        // Those words appear inside the "lançamentos" table (e.g., "JUROS LIMITE DA CONTA"),
        // causing the extraction to stop prematurely.
        Pattern lancamentosEnd = Pattern.compile(
            "(?i)\\b(lan[cç]amentos\\s+futuros|sa[ií]das\\s+futuras|posi[cç][aã]o\\s+consolidada)\\b");

        Matcher startMatcher = lancamentosStart.matcher(text);
        if (!startMatcher.find()) {
            debug("[PARSER_DEBUG] Seção 'lançamentos' não encontrada. Usando texto completo.");
            return text;
        }

        int startIdx = startMatcher.start();

        Matcher endMatcher = lancamentosEnd.matcher(text);
        endMatcher.region(startIdx, text.length());

        int endIdx = text.length();
        if (endMatcher.find()) {
            endIdx = endMatcher.start();
            debug("[PARSER_DEBUG] Seção 'lançamentos' extraída: chars " + startIdx + " até " + endIdx);
        } else {
            debug("[PARSER_DEBUG] Fim de 'lançamentos' não encontrado. Usando até fim do texto.");
        }

        String result = text.substring(startIdx, endIdx);
        debug("[PARSER_DEBUG] Texto original: " + text.length() + " chars, Extraído: " + result.length() + " chars");
        return result;
    }

    public ParsedBankStatement parse(String rawText) {
        // Extract only "lançamentos" section to avoid duplicates/future transactions.
        String lancamentosOnly = extractLancamentosSection(rawText);

        String normalizedFull = normalize(rawText);
        String normalizedLancamentos = normalize(lancamentosOnly);
        // Use full text to extract statement date (period end) even if outside "lançamentos".
        LocalDate statementDate = extractStatementDate(normalizedFull);

        BigDecimal opening = extractMoney(normalizedFull, OPENING_PATTERN);
        BigDecimal closing = extractMoney(normalizedFull, CLOSING_PATTERN);
        BigDecimal creditLimit = extractMoney(normalizedFull, CREDIT_LIMIT_PATTERN);
        BigDecimal availableLimit = extractMoney(normalizedFull, AVAILABLE_LIMIT_PATTERN);

        List<String> lines = splitLinesSmart(lancamentosOnly);
        List<ParsedTransaction> parsedTransactions = new ArrayList<>();
        for (String line : lines) {
            ParsedTransaction tx = tryParseTransactionLine(line, statementDate);
            if (tx != null) {
                parsedTransactions.add(tx);
            }
        }

        parsedTransactions.sort(Comparator.comparing(ParsedTransaction::transactionDate));

        if (statementDate == null && !parsedTransactions.isEmpty()) {
            statementDate = parsedTransactions.get(parsedTransactions.size() - 1).transactionDate();
        }
        if (statementDate == null) {
            statementDate = LocalDate.now();
        }

        if (opening == null && !parsedTransactions.isEmpty()) {
            ParsedTransaction first = parsedTransactions.get(0);
            if (first.balance() != null && first.amount() != null) {
                opening = first.balance().subtract(first.amount());
            } else {
                opening = BigDecimal.ZERO;
            }
        }

        boolean anyMissingBalance = parsedTransactions.stream().anyMatch(t -> t.balance() == null);
        if (anyMissingBalance) {
            BigDecimal running = opening != null ? opening : BigDecimal.ZERO;
            for (int i = 0; i < parsedTransactions.size(); i++) {
                ParsedTransaction t = parsedTransactions.get(i);
                BigDecimal amount = t.amount() == null ? BigDecimal.ZERO : t.amount();
                BigDecimal nextBalance = t.balance() != null ? t.balance() : running.add(amount);
                if (t.balance() == null) {
                    parsedTransactions.set(i,
                            new ParsedTransaction(t.transactionDate(), t.description(), amount, nextBalance, t.type()));
                }
                running = nextBalance;
            }
            if (closing == null) {
                closing = running;
            }
        }

        if (closing == null && !parsedTransactions.isEmpty()) {
            ParsedTransaction last = parsedTransactions.get(parsedTransactions.size() - 1);
            closing = last.balance() != null ? last.balance() : BigDecimal.ZERO;
        }

        if (opening == null) opening = BigDecimal.ZERO;
        if (closing == null) closing = BigDecimal.ZERO;
        if (creditLimit == null) creditLimit = BigDecimal.ZERO;
        if (availableLimit == null) availableLimit = BigDecimal.ZERO;

        return new ParsedBankStatement(statementDate, opening, closing, creditLimit, availableLimit, parsedTransactions);
    }

    private ParsedTransaction tryParseTransactionLine(String line, LocalDate statementDate) {
        if (line == null) return null;
        String l = line.replace('\u00A0', ' ').trim();
        // Normalize common unicode minus characters that appear in PDFs.
        l = l.replace('−', '-').replace('–', '-').replace('—', '-');
        if (l.isEmpty()) return null;

        debug("[PARSER_DEBUG] Tentando fazer parse: " + l);

        Matcher m = TX_WITH_BALANCE.matcher(l);
        if (m.matches()) {
            debug("[PARSER_DEBUG] ✅ TX_WITH_BALANCE fez match!");
            LocalDate date = parseDate(m.group(1), m.group(2), statementDate);
            if (date == null) return null;
            String description = cleanupDescription(m.group(3));
            String amountStr = m.group(4);
            String dc = m.group(5);
            BigDecimal balance = parseMoneyBr(m.group(6));

            BigDecimal amount = parseMoneyBr(amountStr);
            BankStatementTransaction.Type type = inferType(amount, dc);
            amount = normalizeSignedAmount(amount, type);
            return new ParsedTransaction(date, description, amount, balance, type);
        }

        // Balance-only rows (e.g., SALDO ANTERIOR / SALDO TOTAL DISPONÍVEL DIA)
        // Must come AFTER TX_WITH_BALANCE, otherwise it would swallow real transactions.
        m = BALANCE_ONLY.matcher(l);
        if (m.matches()) {
            debug("[PARSER_DEBUG] ✅ BALANCE_ONLY fez match!");
            LocalDate date = parseDate(m.group(1), m.group(2), statementDate);
            if (date == null) return null;
            String description = cleanupDescription(m.group(3));
            BigDecimal balance = parseMoneyBr(m.group(4));

            if (isBalanceLine(description)) {
                return new ParsedTransaction(date, description, BigDecimal.ZERO, balance, BankStatementTransaction.Type.BALANCE);
            }

            // IMPORTANT: do NOT return null here.
            // Some real transaction lines match the generic "date + description + value" shape.
            // If it isn't a balance line, we must continue and try TX_NO_BALANCE.
        }

        m = TX_NO_BALANCE.matcher(l);
        if (m.matches()) {
            debug("[PARSER_DEBUG] ✅ TX_NO_BALANCE fez match!");
            LocalDate date = parseDate(m.group(1), m.group(2), statementDate);
            if (date == null) return null;
            String description = cleanupDescription(m.group(3));
            BigDecimal amount = parseMoneyBr(m.group(4));
            String dc = m.group(5);

            if (isBalanceLine(description)) {
                return new ParsedTransaction(date, description, BigDecimal.ZERO, null, BankStatementTransaction.Type.BALANCE);
            }

            BankStatementTransaction.Type type = inferType(amount, dc);
            amount = normalizeSignedAmount(amount, type);
            return new ParsedTransaction(date, description, amount, null, type);
        }

        debug("[PARSER_DEBUG] ❌ Nenhum regex fez match!");
        return null;
    }

    private static String cleanupDescription(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("\\s+", " ").trim();
    }

    static boolean isBalanceLine(String description) {
        if (description == null) return false;
        String desc = description.toUpperCase(Locale.ROOT).trim();
        return desc.contains("SALDO ANTERIOR")
                || desc.contains("SALDO TOTAL DISPON")
                || desc.startsWith("SALDO ");
    }

    private static BankStatementTransaction.Type inferType(BigDecimal amount, String dcMarker) {
        if (dcMarker != null) {
            String marker = dcMarker.trim().toUpperCase(Locale.ROOT);
            if (marker.equals("C")) return BankStatementTransaction.Type.CREDIT;
            if (marker.equals("D")) return BankStatementTransaction.Type.DEBIT;
        }
        if (amount == null) return BankStatementTransaction.Type.DEBIT;
        return amount.signum() >= 0 ? BankStatementTransaction.Type.CREDIT : BankStatementTransaction.Type.DEBIT;
    }

    private static BigDecimal normalizeSignedAmount(BigDecimal amount, BankStatementTransaction.Type type) {
        if (amount == null) return BigDecimal.ZERO;

        // DEBIT deve ser negativo
        if (type == BankStatementTransaction.Type.DEBIT) {
            return amount.signum() < 0 ? amount : amount.negate();
        }

        // CREDIT deve ser positivo
        if (type == BankStatementTransaction.Type.CREDIT) {
            return amount.signum() > 0 ? amount : amount.abs();
        }

        return amount;
    }

    private static List<String> splitLines(String rawText) {
        if (rawText == null || rawText.isBlank()) return List.of();
        String t = rawText.replace('\u00A0', ' ');
        String[] arr = t.split("\\r?\\n");
        List<String> out = new ArrayList<>(arr.length);
        for (String s : arr) {
            if (s != null) out.add(s);
        }
        return out;
    }

    private static List<String> splitLinesSmart(String rawText) {
        List<String> base = splitLines(rawText);

        int nonEmpty = 0;
        for (String s : base) {
            if (s != null && !s.trim().isEmpty()) nonEmpty++;
        }

        debug("[PARSER_DEBUG] splitLinesSmart: nonEmpty=" + nonEmpty + ", base.size()=" + base.size());

        // If extraction returned a single long line (or almost), try to split entries by date tokens.
        if (nonEmpty <= 2) {
            debug("[PARSER_DEBUG] Tentando splitByEntryDateTokens...");
            List<String> byDate = splitByEntryDateTokens(rawText);
            debug("[PARSER_DEBUG] byDate.size()=" + byDate.size());
            if (byDate.size() > base.size()) {
                debug("[PARSER_DEBUG] ✅ Usando split inteligente!");
                return byDate;
            }
        }

        debug("[PARSER_DEBUG] ✅ Usando split normal!");
        return base;
    }

    private static List<String> splitByEntryDateTokens(String rawText) {
        if (rawText == null || rawText.isBlank()) return List.of();

        String t = rawText.replace('\u00A0', ' ').trim();
        if (t.isEmpty()) return List.of();

        Matcher m = ENTRY_DATE_TOKEN.matcher(t);
        List<Integer> starts = new ArrayList<>();
        while (m.find()) {
            // group(1) points to the actual date token start
            starts.add(m.start(1));
        }

        if (starts.size() <= 1) {
            return splitLines(rawText);
        }

        List<String> out = new ArrayList<>(starts.size());
        for (int i = 0; i < starts.size(); i++) {
            int start = starts.get(i);
            int end = (i + 1 < starts.size()) ? starts.get(i + 1) : t.length();
            if (start >= 0 && start < end && end <= t.length()) {
                String chunk = t.substring(start, end).trim();
                if (!chunk.isEmpty()) {
                    out.add(chunk);
                }
            }
        }

        return out;
    }

    private static String normalize(String rawText) {
        if (rawText == null) return "";
        String t = rawText.replace('\u00A0', ' ');
        return t.replaceAll("[\\t ]+", " ");
    }

    private static LocalDate extractStatementDate(String text) {
        if (text == null || text.isBlank()) return null;
        Matcher m = PERIOD_PATTERN.matcher(text);
        if (m.find()) {
            LocalDate end = parseDateFull(m.group(2));
            if (end != null) return end;
        }

        LocalDate last = null;
        m = DATE_FULL_PATTERN.matcher(text);
        while (m.find()) {
            LocalDate d = parseDateFull(m.group(1));
            if (d != null) last = d;
        }
        return last;
    }

    private static LocalDate parseDate(String ddMM, String yyyy, LocalDate statementDate) {
        if (ddMM == null) return null;

        Integer year = null;
        if (yyyy != null && !yyyy.isBlank()) {
            try {
                year = Integer.parseInt(yyyy.trim());
            } catch (NumberFormatException ignored) {
                year = null;
            }
        }
        if (year == null) {
            year = (statementDate != null) ? statementDate.getYear() : Year.now().getValue();
        }

        try {
            DateTimeFormatter f = DateTimeFormatter.ofPattern("dd/MM/uuuu", LOCALE_PT_BR);
            return LocalDate.parse(ddMM.trim() + "/" + year, f);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static LocalDate parseDateFull(String ddMMyyyy) {
        if (ddMMyyyy == null || ddMMyyyy.isBlank()) return null;
        try {
            DateTimeFormatter f = DateTimeFormatter.ofPattern("dd/MM/uuuu", LOCALE_PT_BR);
            return LocalDate.parse(ddMMyyyy.trim(), f);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static BigDecimal extractMoney(String text, Pattern p) {
        if (text == null || text.isBlank()) return null;
        Matcher m = p.matcher(text);
        if (m.find()) {
            return parseMoneyBr(m.group(2));
        }
        return null;
    }

    private static BigDecimal parseMoneyBr(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;

        // Handle trailing minus (e.g., "943,49-") and unicode minus already normalized.
        if (t.endsWith("-") && !t.startsWith("-")) {
            t = "-" + t.substring(0, t.length() - 1).trim();
        }

        t = t.replace(".", "").replace(",", ".");
        try {
            return new BigDecimal(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void debug(String msg) {
        if (!DEBUG) return;
        System.err.println(msg);
    }

    @Getter
    public static final class ParsedBankStatement {
        private final LocalDate statementDate;
        private final BigDecimal openingBalance;
        private final BigDecimal closingBalance;
        private final BigDecimal creditLimit;
        private final BigDecimal availableLimit;
        private final List<ParsedTransaction> transactions;

        public ParsedBankStatement(
                LocalDate statementDate,
                BigDecimal openingBalance,
                BigDecimal closingBalance,
                BigDecimal creditLimit,
                BigDecimal availableLimit,
                List<ParsedTransaction> transactions) {
            this.statementDate = statementDate;
            this.openingBalance = openingBalance;
            this.closingBalance = closingBalance;
            this.creditLimit = creditLimit;
            this.availableLimit = availableLimit;
            this.transactions = transactions == null ? List.of() : List.copyOf(transactions);
        }
    }

    public record ParsedTransaction(
            LocalDate transactionDate,
            String description,
            BigDecimal amount,
            BigDecimal balance,
            BankStatementTransaction.Type type) {
    }
}
