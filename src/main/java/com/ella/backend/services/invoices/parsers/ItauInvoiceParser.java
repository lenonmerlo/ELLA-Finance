package com.ella.backend.services.invoices.parsers;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ella.backend.enums.TransactionScope;
import com.ella.backend.enums.TransactionType;

public class ItauInvoiceParser implements InvoiceParserStrategy {

    private static final Logger log = LoggerFactory.getLogger(ItauInvoiceParser.class);

    @Override
    public boolean isApplicable(String text) {
        if (text == null) return false;
        String n = normalizeForSearch(text)
                .replaceAll("\\s+", " ")
                .trim();

        // Detecção robusta do Itaú:
        // - Não basta "itau" solto: exige marcadores e/ou âncoras de layout.
        boolean hasItauMention = n.contains("itau");
        if (!hasItauMention) return false;

        boolean hasItauMarkers = n.contains("banco itau")
            || n.contains("itau unibanco")
            || n.contains("itaucard")
            || n.contains("itau personnalite")
            || n.contains("itau personalite")
            || n.contains("itau uniclass")
            || n.contains("itauuniclass")
            || n.contains("limite total de credito")
            || n.contains("limite disponivel");

        boolean hasPayments = n.contains("pagamentos efetuados")
            || n.contains("pagamento efetuado")
            || (n.contains("pagamentos") && (n.contains("efetuad") || n.contains("realiz")));

        boolean hasPurchasesSection = n.contains("lancamentos: compras e saques")
            || n.contains("lancamentos no cartao")
            || (n.contains("lancamentos") && (n.contains("compras") || n.contains("saques")));

        boolean hasSummaryMarkers = (n.contains("resumo da fatura") || n.contains("resumo da fatura em"))
            && (n.contains("total desta fatura") || n.contains("total da fatura") || n.contains("o total da sua fatura"))
            && (n.contains("pagamento minimo") || n.contains("pagamento mínimo"));

        boolean hasInstallmentSection = n.contains("compras parceladas")
            && (n.contains("proximas faturas") || n.contains("proxima fatura") || (n.contains("proxim") && n.contains("fatura")));

        // Padrão de linha típico do Itaú: dd/MM descrição [NN/TT] valor
        boolean hasItauLinePattern = false;
        try {
            String raw = text == null ? "" : text;
            hasItauLinePattern = Pattern.compile(
                "(?is)\\b\\d{2}/\\d{2}\\s+[A-Z0-9][A-Z0-9\\s\\*\\-\\./]{3,}\\s+(?:\\d{2}/\\d{2}\\s+)?\\d+[\\.,]\\d{2}\\b")
                .matcher(raw)
                .find();
        } catch (Exception ignored) {
            hasItauLinePattern = false;
        }

        boolean hasSectionMarkers = hasPayments && hasPurchasesSection;

        return hasSectionMarkers
            || (hasItauMarkers && hasSummaryMarkers)
            || (hasItauMarkers && hasItauLinePattern)
            || (hasSummaryMarkers && hasInstallmentSection);
    }

    @Override
    public LocalDate extractDueDate(String text) {
        if (text == null || text.isBlank()) return null;
        String normalizedText = normalizeNumericDates(text);
        Integer inferredYear = inferYearFromText(normalizedText);

        // Em alguns PDFs do Itaú existem múltiplas datas no cabeçalho (ex.: próxima fatura/postagem).
        // Estratégia: coletar candidatos e escolher o mais provável (perto do "total da fatura" e longe de "próxima").
        String search = normalizeForSearch(normalizedText).replaceAll("\\s+", " ");

        record Candidate(LocalDate date, int score) {}
        Candidate best = null;

        List<String> summaryAnchors = List.of(
                "o total da sua fatura",
                "total desta fatura",
                "total da fatura",
                "resumo da fatura"
        );

        java.util.function.BiFunction<Integer, Integer, Integer> scoreAt = (start, end) -> {
            int s = 0;
            int from = Math.max(0, start - 140);
            int to = Math.min(search.length(), end + 140);
            String ctx = search.substring(from, to);

            // Penaliza contexto de "próxima" (próxima fatura/postagem etc)
            if (ctx.contains("proxim") || ctx.contains("postag") || ctx.contains("process") || ctx.contains("entrada")) {
                s -= 50;
            }
            // Bonus se estiver próximo do resumo/total
            for (String anchor : summaryAnchors) {
                int idx = search.lastIndexOf(anchor, start);
                if (idx >= 0 && (start - idx) <= 420) {
                    s += 80;
                    break;
                }
            }
            // Bonus se for "com vencimento em"
            if (ctx.contains("com vencimento em")) {
                s += 30;
            }
            return s;
        };

        // 0) Procurar todas ocorrências de "com vencimento em" e escolher a melhor.
        Matcher prio = Pattern.compile(
                "(?is)com\\s+vencimento\\s+em\\s*[:]?\\s*(\\d{2})\\s*[\\./-]\\s*(\\d{2})\\s*[\\./-]\\s*(\\d{4})")
                .matcher(normalizedText);
        while (prio.find()) {
            LocalDate d = safeDate(parseIntOrNull(prio.group(3)), parseIntOrNull(prio.group(2)), parseIntOrNull(prio.group(1)));
            if (d == null) continue;
            int score = scoreAt.apply(prio.start(), prio.end());
            if (best == null || score > best.score) {
                best = new Candidate(d, score);
            }
        }

        // 1) Fallback: procurar candidatos de vencimento/vcto/data de vencimento e escolher a melhor.
        Matcher mAny = Pattern.compile(
                "(?is)\\b(venc(?:imento)?|vct(?:o)?|data\\s+de\\s+vencimento)\\b[^\\d]{0,60}(\\d{2})\\s*[\\./-]\\s*(\\d{2})(?:\\s*[\\./-]\\s*(\\d{4}))?")
                .matcher(normalizedText);
        while (mAny.find()) {
            Integer day = parseIntOrNull(mAny.group(2));
            Integer month = parseIntOrNull(mAny.group(3));
            Integer year = parseIntOrNull(mAny.group(4));
            if (year == null) year = inferredYear != null ? inferredYear : LocalDate.now().getYear();
            LocalDate d = safeDate(year, month, day);
            if (d == null) continue;
            int score = scoreAt.apply(mAny.start(), mAny.end());
            if (best == null || score > best.score) {
                best = new Candidate(d, score);
            }
        }

        if (best != null) {
            log.debug("[ItauParser] Due date selected: {} (score={})", best.date, best.score);
            return best.date;
        }

        // PRIORITÁRIO (fatura atual): "Com vencimento em: DD/MM/YYYY".
        // Evita falso positivo do bloco de processamento ("Vencimento: 14/01/2026") quando ambos existem.
        List<Pattern> patterns = List.of(
            Pattern.compile(
                "(?is)com\\s+vencimento\\s+em\\s*[:]?\\s*(\\d{2})\\s*[\\./-]\\s*(\\d{2})\\s*[\\./-]\\s*(\\d{4})"),
            // Itaú costuma variar: "Vencimento", "VENC.", "VCTO" e às vezes sem ano (dd/MM).
            // Numérico com ano (dd/MM/yyyy) ou com separadores variados.
            Pattern.compile(
                "(?is)(?:\\b(venc(?:imento)?|vct(?:o)?|data\\s+de\\s+vencimento)\\b)" +
                    "[^\\d]{0,40}(\\d{2})\\s*[\\./-]\\s*(\\d{2})\\s*[\\./-]\\s*(\\d{4})"),
            // Numérico sem ano (dd/MM). Inferimos o ano via qualquer dd/MM/yyyy presente no documento.
            Pattern.compile(
                "(?is)(?:\\b(venc(?:imento)?|vct(?:o)?|data\\s+de\\s+vencimento)\\b)" +
                    "[^\\d]{0,40}(\\d{2})\\s*[\\./-]\\s*(\\d{2})(?!\\s*[\\./-]\\s*\\d{4})"),
            // Textual: "Vencimento 12 DEZ 2025"
            Pattern.compile(
                "(?is)(?:\\b(venc(?:imento)?|vct(?:o)?|data\\s+de\\s+vencimento)\\b)" +
                    "[^\\d]{0,40}(\\d{2})\\s+([A-Z]{3})\\s+(\\d{4})")
        );

        for (Pattern p : patterns) {
            Matcher m = p.matcher(normalizedText);
            if (!m.find()) continue;

            LocalDate d = extractDueDateFromMatcher(m, inferredYear);
            if (d != null) return d;
        }

        // 4) Ultra-flexível: dígitos perto do keyword, mesmo sem separadores (ou com cada dígito separado por espaço).
        // Ex: "Vencimento: 2 2 1 2 2 0 2 5" ou "Vencimento 22 12 2025".
        try {
            String raw = text.replace('\u00A0', ' ');
            Matcher m4 = Pattern.compile(
                    "(?is)\\b(venc(?:imento)?|vct(?:o)?|data\\s+de\\s+vencimento)\\b[^0-9]{0,60}([0-9][0-9\\s\\./-]{5,30})")
                    .matcher(raw);
            if (m4.find()) {
                String digits = m4.group(2).replaceAll("\\D", "");
                if (digits.length() >= 8) {
                    Integer day = parseIntOrNull(digits.substring(0, 2));
                    Integer month = parseIntOrNull(digits.substring(2, 4));
                    Integer year = parseIntOrNull(digits.substring(4, 8));
                    LocalDate d = safeDate(year, month, day);
                    if (d != null) return d;
                }
                if (digits.length() >= 4 && inferredYear != null) {
                    // ddMM sem ano
                    Integer day = parseIntOrNull(digits.substring(0, 2));
                    Integer month = parseIntOrNull(digits.substring(2, 4));
                    LocalDate d = safeDate(inferredYear, month, day);
                    if (d != null) return d;
                }
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    private LocalDate extractDueDateFromMatcher(Matcher matcher, Integer inferredYear) {
        try {
            if (matcher == null) return null;
            int groupCount = matcher.groupCount();

            // Caso 1: "com vencimento em" => (day, month, year)
            if (groupCount == 3
                    && matcher.group(1) != null
                    && matcher.group(3) != null
                    && matcher.group(1).matches("\\d{2}")
                    && matcher.group(3).matches("\\d{4}")) {
                return safeDate(
                        parseIntOrNull(matcher.group(3)),
                        parseIntOrNull(matcher.group(2)),
                        parseIntOrNull(matcher.group(1))
                );
            }

            // Caso 2: numérico com ano => (keyword, day, month, year)
            if (groupCount >= 4 && matcher.group(2) != null && matcher.group(4) != null
                    && matcher.group(2).matches("\\d{2}") && matcher.group(4).matches("\\d{4}")) {
                // Caso textual: (keyword, day, MON, year)
                if (matcher.group(3) != null && matcher.group(3).matches("[A-Z]{3}")) {
                    Integer day = parseIntOrNull(matcher.group(2));
                    Integer month = monthAbbrevToNumber(matcher.group(3));
                    Integer year = parseIntOrNull(matcher.group(4));
                    return safeDate(year, month, day);
                }

                // Caso numérico: (keyword, day, month, year)
                return safeDate(
                        parseIntOrNull(matcher.group(4)),
                        parseIntOrNull(matcher.group(3)),
                        parseIntOrNull(matcher.group(2))
                );
            }

            // Caso 3: numérico sem ano => (keyword, day, month)
            if (groupCount == 3 && matcher.group(2) != null && matcher.group(3) != null
                    && matcher.group(2).matches("\\d{2}") && matcher.group(3).matches("\\d{2}")) {
                Integer year = inferredYear != null ? inferredYear : LocalDate.now().getYear();
                return safeDate(year, parseIntOrNull(matcher.group(3)), parseIntOrNull(matcher.group(2)));
            }

            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    @Override
    public List<TransactionData> extractTransactions(String text) {
        if (text == null || text.isBlank()) return List.of();

        List<TransactionData> transactions = new ArrayList<>();

        // Important: Itaú PDFs often omit the year in transaction rows (dd/MM or dd MMM).
        // We infer the year from any dd/MM/yyyy present in the document (usually the due date).
        Integer inferredYear = inferYearFromText(normalizeNumericDates(text));

        enum PdfSection { NONE, PAYMENTS, PURCHASES, INSTALLMENTS_FUTURE }
        PdfSection section = PdfSection.NONE;
        boolean stopParsing = false;

        record PendingRow(String dateStr, String description) {
        }
        PendingRow pending = null;

        Pattern dateAndDescPattern = Pattern.compile(
            "^(\\d{2}\\s+[A-Za-z]{3}|\\d{2}/\\d{2}(?:/\\d{4})?)\\s+(.+?)\\s*$");
        Pattern amountOnlyPattern = Pattern.compile("^-?[0-9][0-9\\.,]*$");

        String[] lines = text.split("\\r?\\n");
        for (String line : lines) {
            if (stopParsing) break;
            String sectionLine = normalizeSectionLine(line);

            if (sectionLine.contains("pagamentos efetuados")) {
                section = PdfSection.PAYMENTS;
                pending = null;
                continue;
            }
            if (sectionLine.contains("lancamentos: compras e saques") || sectionLine.contains("lancamentos no cartao")) {
                section = PdfSection.PURCHASES;
                pending = null;
                continue;
            }

            // Se o PDF não expõe a linha "Compras parceladas - próximas faturas" via PDFBox,
            // ainda podemos parar no subtotal "Total dos lançamentos atuais" (que encerra a fatura atual).
            if (section == PdfSection.PURCHASES && (sectionLine.contains("total dos lancamentos atuais")
                    || sectionLine.contains("total dos lancamentos"))) {
                stopParsing = true;
                pending = null;
                continue;
            }

            if (sectionLine.contains("compras parceladas") || sectionLine.contains("proximas faturas") || sectionLine.contains("proxima fatura")) {
                section = PdfSection.INSTALLMENTS_FUTURE;
                // Importante: nada após "próximas faturas" pertence à fatura atual.
                stopParsing = true;
                pending = null;
                continue;
            }
            if (sectionLine.contains("encargos cobrados nesta fatura")
                    || sectionLine.contains("novo teto")
                    || sectionLine.contains("credito rotativo")
                    || sectionLine.contains("limites de credito")
                    || sectionLine.contains("juros do rotativo")
                    || sectionLine.contains("juros de mora")
                    || sectionLine.contains("fique atento")
                    || sectionLine.startsWith("sac")) {
                section = PdfSection.NONE;
                pending = null;
                continue;
            }

            boolean shouldParse = (section == PdfSection.PAYMENTS || section == PdfSection.PURCHASES);
            if (!shouldParse) continue;

            String raw = line == null ? "" : line.trim();
            if (raw.isEmpty()) continue;

            // 1) Tenta parsear linha completa (date + desc + amount)
            TransactionData data = parsePdfLine(raw, inferredYear);
            if (data != null) {
                pending = null;
                if (section != PdfSection.PAYMENTS || !isPaymentFromPreviousInvoice(data.description)) {
                    transactions.add(data);
                }
                continue;
            }

            // 2) Se a tabela vier com valor em linha separada, junta (date+desc) + (amount)
            if (pending != null && amountOnlyPattern.matcher(raw.replace("R$", "").trim()).matches()) {
                String amountStr = raw.replace("R$", "").trim();
                amountStr = amountStr.replace(".", "").replace(",", ".");

                try {
                    BigDecimal amount = new BigDecimal(amountStr);
                    LocalDate date = parseDate(pending.dateStr, inferredYear);

                    TransactionType type;
                    if (amount.compareTo(BigDecimal.ZERO) < 0) {
                        type = TransactionType.INCOME;
                    } else {
                        String d = pending.description == null ? "" : pending.description.toLowerCase();
                        if (d.contains("pagamento") || d.contains("payment")) {
                            type = TransactionType.INCOME;
                        } else {
                            type = TransactionType.EXPENSE;
                        }
                    }

                    String category = MerchantCategoryMapper.categorize(pending.description, type);
                    TransactionScope scope = inferScope(pending.description, null);

                    TransactionData joined = new TransactionData(
                            pending.description,
                            amount.abs(),
                            type,
                            category,
                            date,
                            null,
                            scope);
                    applyInstallmentInfo(joined, extractInstallmentInfo(pending.description));
                    if (section != PdfSection.PAYMENTS || !isPaymentFromPreviousInvoice(joined.description)) {
                        transactions.add(joined);
                    }
                } catch (Exception ignored) {
                    // ignore
                }

                pending = null;
                continue;
            }

            // 3) Se a linha tiver date+desc (sem amount), guarda e tenta completar na próxima linha
            Matcher md = dateAndDescPattern.matcher(raw);
            if (md.find()) {
                String dateStr = md.group(1);
                String desc = md.group(2);
                // Evita capturar cabeçalhos/linhas semânticas
                String nDesc = normalizeSectionLine(desc);
                if (!nDesc.startsWith("estabelecimento") && !nDesc.startsWith("valor") && !nDesc.startsWith("data")) {
                    pending = new PendingRow(dateStr, desc);
                }
            }
        }

        return transactions;
    }

    private boolean isPaymentFromPreviousInvoice(String description) {
        if (description == null || description.isBlank()) return false;
        String n = normalizeSectionLine(description)
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        // Ex.: "PAGAMENTO DEB AUTOMATIC" / "PAGAMENTO DEBITADO AUTOMATICAMENTE" / "PAGAMENTO EFETUADO"
        if (!n.startsWith("pagamento")) return false;
        if (n.contains("deb") && n.contains("automatic")) return true;
        if (n.contains("debitado") && n.contains("automatic")) return true;
        if (n.contains("efetuado")) return true;
        return true;
    }

    private String normalizeForSearch(String input) {
        if (input == null) return "";
        return Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase();
    }

    private String normalizeSectionLine(String line) {
        if (line == null) return "";
        String normalized = Normalizer.normalize(line, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .trim();
        return normalized.replaceAll("\\s+", " ");
    }

    private TransactionData parsePdfLine(String line, Integer inferredYear) {
        try {
            if (line == null) return null;
            line = line.trim();
            if (line.isEmpty()) return null;

            Pattern pattern = Pattern.compile("^(\\d{2}\\s+[A-Za-z]{3}|\\d{2}/\\d{2}(?:/\\d{4})?)\\s+(.+?)\\s+(-?[\\d\\.,]+)\\s*$");
            Matcher matcher = pattern.matcher(line);
            if (!matcher.find()) return null;

            String dateStr = matcher.group(1);
            String desc = matcher.group(2);
            String amountStr = matcher.group(3);

            LocalDate date = parseDate(dateStr, inferredYear);

            amountStr = amountStr.replace(".", "").replace(",", ".");
            BigDecimal amount = new BigDecimal(amountStr);

            TransactionType type;
            if (amount.compareTo(BigDecimal.ZERO) < 0) {
                type = TransactionType.INCOME;
            } else {
                String d = desc == null ? "" : desc.toLowerCase();
                if (d.contains("pagamento") || d.contains("payment")) {
                    type = TransactionType.INCOME;
                } else {
                    type = TransactionType.EXPENSE;
                }
            }

            String category = MerchantCategoryMapper.categorize(desc, type);
            TransactionScope scope = inferScope(desc, null);

            TransactionData data = new TransactionData(desc, amount.abs(), type, category, date, null, scope);
            applyInstallmentInfo(data, extractInstallmentInfo(desc));
            return data;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void applyInstallmentInfo(TransactionData data, InstallmentInfo info) {
        if (data == null || info == null) return;
        data.installmentNumber = info.number();
        data.installmentTotal = info.total();
    }

    private InstallmentInfo extractInstallmentInfo(String description) {
        if (description == null || description.isBlank()) return null;
        String normalized = description.toLowerCase();

        Pattern slashPattern = Pattern.compile("(\\d{1,2})\\s*/\\s*(\\d{1,2})");
        Matcher slashMatcher = slashPattern.matcher(normalized);
        if (slashMatcher.find()) {
            int number = Integer.parseInt(slashMatcher.group(1));
            int total = Integer.parseInt(slashMatcher.group(2));
            if (number > 0 && total > 0) {
                return new InstallmentInfo(number, total);
            }
        }

        Pattern timesPattern = Pattern.compile("(\\d{1,2})\\s*x\\s*(\\d{1,2})");
        Matcher timesMatcher = timesPattern.matcher(normalized);
        if (timesMatcher.find()) {
            int number = Integer.parseInt(timesMatcher.group(1));
            int total = Integer.parseInt(timesMatcher.group(2));
            if (number > 0 && total > 0) {
                return new InstallmentInfo(number, total);
            }
        }

        return null;
    }

    private record InstallmentInfo(Integer number, Integer total) {}

    private LocalDate parseDate(String dateStr, Integer inferredYear) {
        try {
            if (dateStr == null) return LocalDate.now();

            if (dateStr.matches("\\d{2}\\s+[A-Za-z]{3}")) {
                String[] parts = dateStr.split("\\s+");
                int day = Integer.parseInt(parts[0]);
                String monthStr = parts[1].toUpperCase();
                int month = switch (monthStr) {
                    case "JAN" -> 1;
                    case "FEV", "FEB" -> 2;
                    case "MAR" -> 3;
                    case "ABR", "APR" -> 4;
                    case "MAI", "MAY" -> 5;
                    case "JUN" -> 6;
                    case "JUL" -> 7;
                    case "AGO", "AUG" -> 8;
                    case "SET", "SEP" -> 9;
                    case "OUT", "OCT" -> 10;
                    case "NOV" -> 11;
                    case "DEZ", "DEC" -> 12;
                    default -> LocalDate.now().getMonthValue();
                };

                int year = inferredYear != null ? inferredYear : LocalDate.now().getYear();
                LocalDate parsed = LocalDate.of(year, month, day);
                if (parsed.isAfter(LocalDate.now().plusMonths(1))) {
                    return parsed.minusYears(1);
                }
                return parsed;
            }

            // dd/MM/yyyy
            if (dateStr.matches("\\d{2}/\\d{2}/\\d{4}")) {
                return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            }

            // dd/MM
            if (dateStr.matches("\\d{2}/\\d{2}")) {
                LocalDate parsed = LocalDate.parse(dateStr, new java.time.format.DateTimeFormatterBuilder()
                        .appendPattern("dd/MM")
                        .parseDefaulting(java.time.temporal.ChronoField.YEAR, inferredYear != null ? inferredYear : LocalDate.now().getYear())
                        .toFormatter());

                if (parsed.isAfter(LocalDate.now().plusMonths(1))) {
                    return parsed.minusYears(1);
                }
                return parsed;
            }

            return LocalDate.parse(dateStr);
        } catch (Exception ignored) {
            return LocalDate.now();
        }
    }

    private Integer inferYearFromText(String text) {
        try {
            if (text == null || text.isBlank()) return null;
            Matcher m = Pattern.compile("(?s)\\b\\d{2}\\s*/\\s*\\d{2}\\s*/\\s*(\\d{4})\\b").matcher(text);
            if (m.find()) {
                return parseIntOrNull(m.group(1));
            }
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String normalizeNumericDates(String text) {
        if (text == null || text.isBlank()) return "";
        String t = text.replace('\u00A0', ' ');
        // remove espaços entre dígitos (ex: "2 2/1 2/2 0 2 5" -> "22/12/2025")
        t = t.replaceAll("(?<=\\d)\\s+(?=\\d)", "");
        // remove espaços ao redor de separadores de data
        t = t.replaceAll("\\s*([\\./-])\\s*", "$1");
        return t;
    }

    private Integer monthAbbrevToNumber(String abbrev) {
        if (abbrev == null) return null;
        return switch (abbrev.trim().toUpperCase()) {
            case "JAN" -> 1;
            case "FEV", "FEB" -> 2;
            case "MAR" -> 3;
            case "ABR", "APR" -> 4;
            case "MAI", "MAY" -> 5;
            case "JUN" -> 6;
            case "JUL" -> 7;
            case "AGO", "AUG" -> 8;
            case "SET", "SEP" -> 9;
            case "OUT", "OCT" -> 10;
            case "NOV" -> 11;
            case "DEZ", "DEC" -> 12;
            default -> null;
        };
    }

    private Integer parseIntOrNull(String value) {
        try {
            if (value == null) return null;
            String v = value.trim();
            if (v.isEmpty()) return null;
            return Integer.parseInt(v);
        } catch (Exception ignored) {
            return null;
        }
    }

    private LocalDate safeDate(Integer year, Integer month, Integer day) {
        try {
            if (year == null || month == null || day == null) return null;
            return LocalDate.of(year, month, day);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String categorizeDescription(String description, TransactionType type) {
        String d = description == null ? "" : description.toLowerCase();
        if (type == TransactionType.INCOME) {
            return "Outros";
        } else {
            if (d.contains("uber") || d.contains(" 99") || d.contains("99 ") || d.contains("cabify")) return "Transporte";
            if (d.contains("posto") || d.contains("combust") || d.contains("ipiranga") || d.contains("shell") || d.contains("petro")) return "Transporte";
            if (d.contains("ifood") || d.contains("ubereats") || d.contains("restaurante") || d.contains("pizza") || d.contains("padaria") || d.contains("lanchonete")) return "Alimentação";
            if (d.contains("mercado") || d.contains("supermerc") || d.contains("carrefour") || d.contains("pão de açúcar") || d.contains("pao de acucar") || d.contains("assai") || d.contains("atacado")) return "Mercado";
            if (d.contains("netflix") || d.contains("spotify") || d.contains("youtube") || d.contains("prime") || d.contains("disney") || d.contains("hbo")) return "Streaming";
            if (d.contains("farmac") || d.contains("drog") || d.contains("droga") || d.contains("drogaria")) return "Farmácia";
            if (d.contains("hospital") || d.contains("clinica") || d.contains("consulta") || d.contains("medic") || d.contains("otica") || d.contains("ótica")) return "Saúde";
            if (d.contains("academia") || d.contains("gym") || d.contains("smartfit") || d.contains("bluefit")) return "Lazer";
            if (d.contains("internet")) return "Internet";
            if (d.contains("telefone") || d.contains("celular")) return "Celular";
            if (d.contains("aluguel") || d.contains("rent")) return "Aluguel";
            if (d.contains("agua") || d.contains("água")) return "Água";
            if (d.contains("energia") || d.contains("luz")) return "Luz";
            return "Outros";
        }
    }

    private TransactionScope inferScope(String description, String cardName) {
        String d = description == null ? "" : description.toLowerCase();
        String c = cardName == null ? "" : cardName.toLowerCase();

        if (d.contains("cnpj") || d.contains("mei") || d.contains("ltda") || d.contains("eireli")
                || d.contains("pj") || c.contains("cnpj") || c.contains("pj") || c.contains("empresa")) {
            return TransactionScope.BUSINESS;
        }

        if (d.contains("fornecedor") || d.contains("insumo") || d.contains("estoque") || d.contains("maquininha")) {
            return TransactionScope.BUSINESS;
        }

        return TransactionScope.PERSONAL;
    }
}
