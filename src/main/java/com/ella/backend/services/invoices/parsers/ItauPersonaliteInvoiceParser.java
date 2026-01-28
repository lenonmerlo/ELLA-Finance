package com.ella.backend.services.invoices.parsers;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ella.backend.enums.TransactionScope;
import com.ella.backend.enums.TransactionType;
import com.ella.backend.services.invoices.util.NormalizeUtil;

/**
 * Parser específico para faturas Itaú Personalité.
 *
 * Layout típico:
 * - "Lançamentos: compras e saques" ou múltiplas seções "Lançamentos no cartão (final XXXX)"
 * - Linha 1: DD/MM ESTABELECIMENTO VALOR
 * - Linha 2 (opcional): CATEGORIA.CIDADE
 * - Seção "Compras parceladas - próximas faturas" deve ser ignorada.
 */
public class ItauPersonaliteInvoiceParser implements InvoiceParserStrategy, PdfAwareInvoiceParser {

    private static final Logger log = LoggerFactory.getLogger(ItauPersonaliteInvoiceParser.class);

        private static final Pattern HAS_MULTI_CARDS = Pattern.compile(
            "(?is)lan(?:c|ç)?amentos\\s+no\\s+cart(?:a|ã)?o.*final\\s*\\d{4}");

        private static final Pattern PERSONALITE_LABEL = Pattern.compile("(?i)itau\\s+personalite");
        private static final Pattern PERSONNALITE_LABEL = Pattern.compile("(?i)itau\\s+personnalite");

        // PDFBox pode "garblar" a palavra, quebrando em espaços entre letras ou tokens.
        // Ex.: "p e r s o n a l i t e", "person ali te", "personnalit e".
        // Como sempre rodamos em cima de `n` (NormalizeUtil.normalize), acentos já foram removidos.
        private static final Pattern PERSONALITE_GARBLED = Pattern.compile(
            "(?is)p\\s*e\\s*r\\s*s\\s*o\\s*n(?:\\s*n)?\\s*a\\s*l\\s*i\\s*t\\s*e");

        private static final Pattern HAS_LAUNCHES_SECTION = Pattern.compile(
            "(?is)(?:lan(?:c|ç)?amentos\\s*(?:[:：]?\\s*)?compras\\s+e\\s+saques|lan(?:c|ç)?amentos\\s+atuais\\b)");

        private static final Pattern CARD_SECTION = Pattern.compile(
            "(?i)lan(?:c|ç)?amentos\\s+no\\s+cart(?:a|ã)?o\\s*\\(\\s*final\\s+(\\d{4})\\s*\\)");

    // PDFBox pode quebrar palavras no meio (ex.: "pr\nximas").
    // Para evitar vazamento de "próximas faturas" na fatura atual, cortamos no primeiro "Compras parceladas".
    private static final Pattern INSTALLMENTS_SECTION = Pattern.compile("(?is)compras\\s+parceladas");

    private static final Pattern MONEY_AT_END = Pattern.compile(
            "(?:R\\$\\s*)?(-?(?:\\d{1,3}(?:\\.\\d{3})*,\\d{2}|\\d+[\\.,]\\d{2}))\\s*$");

    private static final Pattern TX_DATE_AT_START = Pattern.compile("^\\d{2}/\\d{2}\\b");

    private static final Pattern INSTALLMENT_FRACTION = Pattern.compile("(\\d{1,2})\\s*/\\s*(\\d{1,2})");

    private static final String CARD_LABEL = "Itau Personnalitê";
    private static final String CARD_LABEL_MC = "Itau Personnalitê Mastercard";

    private static final Pattern HEADER_LINE = Pattern.compile(
            "(?i).*(data\\s+estabelecimento\\s+valor|data\\s+lan[cç]amentos\\s+valor|estabelecimento\\s+valor|valor\\s+em\\s+r\\$).*");

    private final ItauInvoiceParser itauDueDateDelegate = new ItauInvoiceParser();

    private final EllaExtractorClient ellaExtractorClient;

    public ItauPersonaliteInvoiceParser() {
        this(new EllaExtractorClient());
    }

    ItauPersonaliteInvoiceParser(EllaExtractorClient ellaExtractorClient) {
        this.ellaExtractorClient = Objects.requireNonNull(ellaExtractorClient);
    }

    @Override
    public boolean isApplicable(String text) {
        if (text == null || text.isBlank()) return false;

        String n = NormalizeUtil.normalize(text);

        // A versão "n" é a única base confiável aqui: PDFBox pode perder acentos/letras e inserir quebras.

        // ✅ MARCADOR 1: Keywords Itaú (tolerante a acentos/variações)
        boolean hasItauMarker = NormalizeUtil.containsKeyword(n, "itau")
            || NormalizeUtil.containsKeyword(n, "banco itau")
            || NormalizeUtil.containsKeyword(n, "itau unibanco")
            || NormalizeUtil.containsKeyword(n, "unibanco")
            || NormalizeUtil.containsKeyword(n, "itaucard")
            || NormalizeUtil.containsKeyword(n, "itau card")
            // PDFBox às vezes perde o 'u' em "Itaú" e vira "Ita ..."
            || NormalizeUtil.containsKeyword(n, "ita unibanco")
            || NormalizeUtil.containsKeyword(n, "ita cares")
            || NormalizeUtil.containsKeyword(n, "itau cares")
            || NormalizeUtil.containsKeyword(n, "itacares")
            || NormalizeUtil.containsKeyword(n, "itaucares");

        // Em algumas faturas o texto "Personnalité" pode estar apenas no logo/arte e não ser extraído pelo PDFBox.
        // Nesses casos, detectamos o layout Itaú (fatura) + marcadores premium como substituto.
        boolean hasItauCares = n.contains("ita cares") || n.contains("itau cares") || n.contains("itacares") || n.contains("itaucares");
        boolean hasUnibancoHolding = n.contains("unibanco holding") || n.contains("itau unibanco holding") || n.contains("ita unibanco holding");
        boolean hasPremiumCardMarker = n.contains("mastercard black")
            || (n.contains("mastercard") && n.contains("black"))
            || n.contains("visa infinite")
            || (n.contains("visa") && n.contains("infinite"));

        // ✅ MARCADOR 2: Keywords Personalité (tolerante a acentos)
        boolean hasPersonaliteMarker = NormalizeUtil.containsKeyword(n, "personalite")
            || NormalizeUtil.containsKeyword(n, "personnalite")
            || PERSONALITE_LABEL.matcher(n).find()
            || PERSONNALITE_LABEL.matcher(n).find()
            || PERSONALITE_GARBLED.matcher(n).find();

        // ✅ MARCADORES DE LAYOUT (genéricos de fatura)
        boolean hasResumoFatura = n.contains("resumo da fatura") || n.contains("resumo da fatura em r$");
        boolean hasLaunchesAtuais = n.contains("lancamentos atuais") || n.contains("lanamentos atuais");
        boolean hasParcelamentoFatura = n.contains("parcelamento da fatura") || (n.contains("parcelamento") && n.contains("fatura"));
        boolean hasPagamentoMinimo = n.contains("pagamento minimo") || n.contains("pagamentomnimo") || n.contains("pagamento minimo:");
        boolean hasItauInvoiceTotal = n.contains("o total da sua fatura") || n.contains("total desta fatura") || n.contains("total da fatura");
        boolean hasTotalLancamentosAtuais = n.contains("total dos lancamentos atuais") || n.contains("total dos lanamentos atuais");

        // ✅ MARCADOR 3: Múltiplos cartões com "final XXXX" (aceita 'cartão/cartao/carto')
        boolean hasMultipleCards = HAS_MULTI_CARDS.matcher(n).find();

        // ✅ MARCADOR 4: Seção de lançamentos (tolerante a "Lanamentos")
        boolean hasLaunchesSection = HAS_LAUNCHES_SECTION.matcher(n).find();

        // Weak hints (não devem aceitar sozinho, só ajudam quando combinado com Personalité + layout típico)
        boolean hasWeakItauHints = n.contains("cartao") || n.contains("carto") || n.contains("fatura");

        // Heurística importante: "Resumo da fatura", "lançamentos atuais" e "total dos lançamentos atuais"
        // também aparecem em fatura Itaú REGULAR.
        // Para não roubar Itaú regular, o Personalité precisa de evidência mais específica:
        // - múltiplos cartões / "final 1234" (layout típico Personalité), OU
        // - marcadores premium muito fortes (ex.: "visa infinite" / "mastercard black"), OU
        // - "personalite/personnalite" explícito.
        int personaliteLayoutCount = 0;
        if (hasMultipleCards) personaliteLayoutCount++;
        if (hasPremiumCardMarker) personaliteLayoutCount++;

        boolean hasAnyInvoiceLayout = hasResumoFatura
            || hasParcelamentoFatura
            || hasPagamentoMinimo
            || hasItauInvoiceTotal
            || hasLaunchesSection
            || hasMultipleCards
            || hasLaunchesAtuais
            || hasTotalLancamentosAtuais;

        // Caso real: PDFBox pode não extrair o "ITAU" do header.
        // Sem marcador de banco, aceitamos se houver evidência forte de Personalité (ex.: "final 1234"),
        // caso contrário exigimos mais de um indício.
        int requiredPersonaliteLayout = hasItauMarker ? 1 : ((hasMultipleCards || hasPremiumCardMarker) ? 1 : 2);
        boolean personaliteLayoutMatch = personaliteLayoutCount >= requiredPersonaliteLayout;

        boolean result;
        if (hasPersonaliteMarker) {
            // Se o texto cita Personalité explicitamente, aceitamos com qualquer layout de fatura.
            result = hasAnyInvoiceLayout;
        } else {
            // Sem "personalite" no texto, NUNCA aceitar só por "resumo/lançamentos/total".
            // Exigimos evidência específica ("final 1234") ou premium forte.
            result = hasAnyInvoiceLayout && personaliteLayoutMatch;
        }

        if (result) {
            log.debug(
                "[ItauPersonalite] ACCEPTED: itau={}, personalite={}, cards={}, launches={}, atuais={}, totalAtuais={}, resumo={}, personaliteLayoutCount={}, requiredPersonaliteLayout={} ",
                hasItauMarker,
                hasPersonaliteMarker,
                hasMultipleCards,
                hasLaunchesSection,
                hasLaunchesAtuais,
                hasTotalLancamentosAtuais,
                hasResumoFatura,
                personaliteLayoutCount,
                requiredPersonaliteLayout);
        } else {
            log.debug(
                "[ItauPersonalite] REJECTED: itau={}, personalite={}, cards={}, launches={}, atuais={}, totalAtuais={}, resumo={}, personaliteLayoutCount={}, requiredPersonaliteLayout={} ",
                hasItauMarker,
                hasPersonaliteMarker,
                hasMultipleCards,
                hasLaunchesSection,
                hasLaunchesAtuais,
                hasTotalLancamentosAtuais,
                hasResumoFatura,
                personaliteLayoutCount,
                requiredPersonaliteLayout);
        }

        return result;
    }

    @Override
    public LocalDate extractDueDate(String text) {
        // Reutiliza a extração de vencimento robusta do Itaú regular.
        return itauDueDateDelegate.extractDueDate(text);
    }

    @Override
    public List<TransactionData> extractTransactions(String text) {
        if (text == null || text.isBlank()) return List.of();

        LocalDate dueDate = extractDueDate(text);
        Integer inferredYear = inferYearFromText(text);
        if (dueDate == null) {
            // Ainda assim tentamos extrair transações; o pipeline pode aplicar fallback de vencimento.
            log.warn("[ItauPersonalite] dueDate not found by parser; continuing with tx extraction");
        }

        // IMPORTANT: PDFBox can extract the transaction list outside of the "Lançamentos: compras e saques" block.
        // The most reliable cut is: keep everything BEFORE the installments heading.
        String beforeInstallments = cutBeforeInstallments(text);

        // If for some reason the text has no installment heading, fall back to the launches section heuristic.
        // (Keeps compatibility with older layouts.)
        if (beforeInstallments == null || beforeInstallments.isBlank()) {
            String launchesSection = extractLaunchesSection(text);
            if (launchesSection == null || launchesSection.isBlank()) {
                return List.of();
            }
            beforeInstallments = launchesSection;
        }

        boolean isMastercard = normalizeForSearch(text).contains("mastercard");

        List<TransactionData> transactions = new ArrayList<>();
        String currentCardName = isMastercard ? CARD_LABEL_MC : CARD_LABEL;

        String[] lines = beforeInstallments.split("\\r?\\n");
        for (int i = 0; i < lines.length; i++) {
            String rawLine = lines[i] == null ? "" : lines[i];
            String line = rawLine.trim();

            if (line.isEmpty()) continue;

            // Atualiza cartão corrente quando encontrar uma seção "final XXXX".
            Matcher mc = CARD_SECTION.matcher(line);
            if (mc.find()) {
                String last4 = mc.group(1);
                if (last4 != null && !last4.isBlank()) {
                    currentCardName = (isMastercard ? CARD_LABEL_MC : CARD_LABEL) + " final " + last4;
                }
                continue;
            }

            // Pular cabeçalhos e linhas que não são transações.
            if (HEADER_LINE.matcher(line).matches()) continue;
            String nLine = normalizeForSearch(line);
            if (nLine.startsWith("lancamentos") || nLine.startsWith("lancamentos no cartao")) continue;
            if (nLine.startsWith("lanamentos") || nLine.startsWith("lanamentos no cartao") || nLine.startsWith("lanamentos no carto")) continue;
            if (nLine.startsWith("lancamentos atuais") || nLine.startsWith("lanamentos atuais")) continue;
            if (nLine.startsWith("total dos lancamentos atuais") || nLine.startsWith("total dos lanamentos atuais")) continue;
            // Evita registrar pagamentos/linhas de resumo como transações (caso apareçam antes da seção de lançamentos).
            if ((nLine.contains("pagamento") && (nLine.contains("deb") || nLine.contains("automatic") || nLine.contains("debitad") || nLine.contains("efetuad")))
                    || nLine.startsWith("total dos pagamentos")
                    || nLine.contains(" total dos pagamentos")) {
                continue;
            }
            if (nLine.contains("vencimento")) continue;
            if (nLine.contains("emissao") || nLine.contains("emisso")) continue;
            if (nLine.contains("postagem")) continue;
            if (nLine.contains("previsao") || nLine.contains("previso") || nLine.contains("fechamento")) continue;
            if (nLine.contains("periodo") || nLine.contains("peodo")) continue;
            if (nLine.contains("continua")) continue;

            // Alguns PDFs trazem um símbolo antes da data (ex.: ícone de celular). Removemos tudo que não for dígito no início.
            line = stripLeadingNonDigit(line);
            if (line.isEmpty()) continue;

            // Guardrails: avoid parsing non-transaction date lines.
            if (line.matches("^\\d{2}/\\d{2}/\\d{4}[\\.)]?$")) continue;
            if (line.matches("^\\d{2}/\\d{2}\\s+a\\s+\\d{2}/\\d{2}\\)?$")) continue;

            // Somente tentamos parsear se começar com data.
            if (!TX_DATE_AT_START.matcher(line).find()) continue;

            String categoryFromNextLine = null;
            if (i + 1 < lines.length) {
                String next = lines[i + 1] == null ? "" : lines[i + 1].trim();
                String nextNorm = normalizeForSearch(next);
                // Segunda linha: CATEGORIA.CIDADE (não começa com data)
                if (!next.isEmpty() && !TX_DATE_AT_START.matcher(stripLeadingNonDigit(next)).find()
                        && nextNorm.contains(".")
                        && !HEADER_LINE.matcher(next).matches()) {
                    categoryFromNextLine = extractCategoryFromCategoryCityLine(next);
                }
            }

            TransactionData tx = tryParseTransactionLine(line, dueDate, inferredYear, currentCardName, categoryFromNextLine);
            if (tx != null) {
                transactions.add(tx);
            } else {
                log.warn("[ItauPersonalite] Failed to parse tx line: {}", line);
            }
        }

        // Dedupe defensive: "Compras parceladas - próximas faturas" often repeats the same installment entries
        // with the installment fraction incremented (e.g., 07/10 -> 08/10). Keep the lowest installmentNumber.
        return dedupeByInstallmentKey(transactions);
    }

    @Override
    public ParseResult parseWithPdf(byte[] pdfBytes, String extractedText) {
        try {
            EllaExtractorClient.ItauPersonnaliteResponse resp = ellaExtractorClient.parseItauPersonnalite(pdfBytes);
            if (resp == null || resp.transactions() == null) {
                throw new IllegalStateException("ella-extractor returned null response");
            }

            LocalDate dueDate = parseIsoDateOrNull(resp.dueDate());
            Integer inferredYear = inferYearFromText(extractedText);
            boolean isMastercard = normalizeForSearch(extractedText).contains("mastercard");

            List<TransactionData> out = new ArrayList<>();
            for (EllaExtractorClient.ItauPersonnaliteResponse.Tx tx : resp.transactions()) {
                if (tx == null) continue;
                String desc = tx.description();
                if (desc == null) continue;
                desc = normalizeSpaces(desc);
                if (desc.isEmpty()) continue;

                BigDecimal amount;
                try {
                    amount = tx.amount() == null ? null : BigDecimal.valueOf(tx.amount());
                } catch (Exception ignored) {
                    amount = null;
                }
                if (amount == null) continue;

                TransactionType type;
                if (amount.compareTo(BigDecimal.ZERO) < 0) {
                    type = TransactionType.INCOME;
                    amount = amount.abs();
                } else {
                    String nd = normalizeForSearch(desc);
                    if (nd.contains("estorno") || nd.contains("credito") || nd.contains("crédito")) {
                        type = TransactionType.INCOME;
                    } else {
                        type = TransactionType.EXPENSE;
                    }
                }

                Integer instNum = null;
                Integer instTot = null;
                if (tx.installment() != null) {
                    instNum = tx.installment().current();
                    instTot = tx.installment().total();
                }

                // If not provided explicitly, attempt to parse installment fraction from description.
                if (instNum == null || instTot == null) {
                    Matcher inst = INSTALLMENT_FRACTION.matcher(desc);
                    while (inst.find()) {
                        instNum = safeInt(inst.group(1));
                        instTot = safeInt(inst.group(2));
                    }
                }

                if (instNum != null && instTot != null) {
                    desc = desc.replaceAll(
                            "\\s*" + Pattern.quote(instNum.toString()) + "\\s*/\\s*" + Pattern.quote(instTot.toString()) + "\\s*",
                            " ");
                    desc = normalizeSpaces(desc);
                }

                LocalDate txDate = parsePythonTxDate(tx.date(), dueDate, inferredYear);
                if (txDate == null) continue;

                String cardName = isMastercard ? CARD_LABEL_MC : CARD_LABEL;
                String last4 = tx.cardFinal();
                if (last4 != null && last4.matches("\\d{4}")) {
                    cardName = cardName + " final " + last4;
                }

                String category = MerchantCategoryMapper.categorize(desc, type);
                TransactionScope scope = TransactionScope.PERSONAL;

                TransactionData td = new TransactionData(
                        desc,
                        amount,
                        type,
                        category,
                        txDate,
                        cardName,
                        scope
                );
                td.installmentNumber = instNum;
                td.installmentTotal = instTot;
                out.add(td);
            }

            out = dedupeByInstallmentKey(out);

            return ParseResult.builder()
                    .transactions(out)
                    .dueDate(dueDate)
                    .build();
        } catch (Exception e) {
            log.warn("[ItauPersonalite] ella-extractor failed; falling back to current text-based parser. reason={}", e.toString());

            LocalDate dueDate = extractDueDate(extractedText);
            List<TransactionData> txs = extractTransactions(extractedText);
            return ParseResult.builder()
                    .transactions(txs)
                    .dueDate(dueDate)
                    .build();
        }
    }

    private static LocalDate parseIsoDateOrNull(String iso) {
        try {
            if (iso == null || iso.isBlank()) return null;
            return LocalDate.parse(iso.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static LocalDate parsePythonTxDate(String dateStr, LocalDate dueDate, Integer inferredYear) {
        if (dateStr == null || dateStr.isBlank()) return null;
        String v = dateStr.trim();

        // Prefer ISO dates.
        try {
            if (v.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return LocalDate.parse(v, DateTimeFormatter.ISO_LOCAL_DATE);
            }
        } catch (Exception ignored) {
        }

        // Accept dd/MM as returned by some debug modes.
        try {
            if (v.matches("\\d{2}/\\d{2}")) {
                return parseDate(v, dueDate, inferredYear);
            }
        } catch (Exception ignored) {
        }

        // Accept dd/MM/yyyy (rare but safe).
        try {
            if (v.matches("\\d{2}/\\d{2}/\\d{4}")) {
                DateTimeFormatter f = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);
                return LocalDate.parse(v, f);
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    private static String cutBeforeInstallments(String text) {
        if (text == null || text.isBlank()) return "";
        Matcher m = INSTALLMENTS_SECTION.matcher(text);
        if (m.find() && m.start() > 0) {
            return text.substring(0, m.start());
        }
        return text;
    }

    private static List<TransactionData> dedupeByInstallmentKey(List<TransactionData> txs) {
        if (txs == null || txs.isEmpty()) return List.of();

        java.util.Map<String, TransactionData> best = new java.util.LinkedHashMap<>();
        for (TransactionData td : txs) {
            if (td == null) continue;
            String key = (td.cardName == null ? "" : td.cardName) + "|"
                + (td.date == null ? "" : td.date.toString()) + "|"
                + (td.amount == null ? "" : td.amount.toPlainString()) + "|"
                + normalizeForSearch(td.description);

            TransactionData existing = best.get(key);
            if (existing == null) {
                best.put(key, td);
                continue;
            }

            Integer a = existing.installmentNumber;
            Integer b = td.installmentNumber;
            if (a != null && b != null) {
                if (b < a) {
                    best.put(key, td);
                }
            }
            // If we can't compare installments, keep the first (stable).
        }

        return new java.util.ArrayList<>(best.values());
    }

    private String extractLaunchesSection(String text) {
        if (text == null || text.isBlank()) return null;

        // Prefer "Lançamentos: compras e saques" when present.
        Pattern p1 = Pattern.compile(
            "(?is)lan(?:c|ç)?amentos\\s*:\\s*compras\\s+e\\s+saques(.*?)(?:compras\\s+parceladas|$)");
        Matcher m1 = p1.matcher(text);
        if (m1.find()) {
            return m1.group(1);
        }

        // Fallback: start at the first "Lançamentos no cartão" block.
        Pattern p2 = Pattern.compile(
            "(?is)(lan(?:c|ç)?amentos\\s+no\\s+cart(?:a|ã)?o\\s*\\(.*?final\\s+\\d{4}\\s*\\))(.*?)(?:compras\\s+parceladas|$)");
        Matcher m2 = p2.matcher(text);
        if (m2.find()) {
            // include the header so we can capture card last4
            return m2.group(1) + "\n" + m2.group(2);
        }

        // Last fallback: return whole text (but it will likely generate false positives; keep it conservative)
        return null;
    }

    private TransactionData tryParseTransactionLine(
            String line,
            LocalDate dueDate,
            Integer inferredYear,
            String cardName,
            String categoryOverride
    ) {
        if (line == null || line.isBlank()) return null;

        String s = line.trim();
        s = stripLeadingNonDigit(s);
        if (s.isEmpty()) return null;

        // Extract date at start.
        String dateStr = null;
        if (s.length() >= 5) {
            dateStr = s.substring(0, 5);
        }
        if (dateStr == null || !dateStr.matches("\\d{2}/\\d{2}")) {
            return null;
        }

        // Robust value extraction: always take the LAST money-looking number at the end.
        Matcher money = MONEY_AT_END.matcher(s);
        if (!money.find()) {
            return null;
        }

        String valueStr = money.group(1);
        int valueStart = money.start(1);

        String establishment = s.substring(5, valueStart).trim();
        establishment = normalizeSpaces(establishment);
        establishment = stripLeadingNonAlnum(establishment);

        if (establishment.isEmpty()) return null;

        // Parse installment fraction when present (e.g. "COS SERVICOSMEDIC07/10" or "... 07/10").
        Integer instNum = null;
        Integer instTot = null;
        Matcher inst = INSTALLMENT_FRACTION.matcher(establishment);
        while (inst.find()) {
            instNum = safeInt(inst.group(1));
            instTot = safeInt(inst.group(2));
        }
        if (instNum != null && instTot != null) {
            establishment = establishment.replaceAll("\\s*" + Pattern.quote(instNum.toString()) + "\\s*/\\s*" + Pattern.quote(instTot.toString()) + "\\s*", " ");
            establishment = normalizeSpaces(establishment);
        }

        BigDecimal amount;
        try {
            amount = parseMoneyBr(valueStr);
        } catch (Exception e) {
            return null;
        }

        TransactionType type;
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            type = TransactionType.INCOME;
            amount = amount.abs();
        } else {
            String nd = normalizeForSearch(establishment);
            if (nd.contains("estorno") || nd.contains("credito") || nd.contains("crédito")) {
                type = TransactionType.INCOME;
            } else {
                type = TransactionType.EXPENSE;
            }
        }

        LocalDate txDate = parseDate(dateStr, dueDate, inferredYear);

        String category = categoryOverride != null && !categoryOverride.isBlank()
                ? categoryOverride
                : MerchantCategoryMapper.categorize(establishment, type);

        TransactionScope scope = TransactionScope.PERSONAL;

        TransactionData td = new TransactionData(
                establishment,
                amount,
                type,
                category,
                txDate,
                cardName,
                scope
        );
        td.installmentNumber = instNum;
        td.installmentTotal = instTot;
        return td;
    }

    private static LocalDate parseDate(String dateStr, LocalDate dueDate, Integer inferredYear) {
        // dateStr = "19/05"
        if (dateStr == null || dateStr.isBlank()) return null;

        Integer day = safeInt(dateStr.substring(0, 2));
        Integer month = safeInt(dateStr.substring(3, 5));
        if (day == null || month == null) return null;

        int year = (dueDate != null ? dueDate.getYear() : (inferredYear != null ? inferredYear : LocalDate.now().getYear()));

        // If tx month is greater than due month, it's from previous year (common when due date is in Jan and tx in Dec).
        if (dueDate != null && month > dueDate.getMonthValue()) {
            year--;
        }

        try {
            return LocalDate.of(year, month, day);
        } catch (Exception e) {
            return null;
        }
    }

    private static BigDecimal parseMoneyBr(String valueStr) {
        if (valueStr == null) return BigDecimal.ZERO;
        String cleaned = valueStr.trim();
        if (cleaned.isEmpty()) return BigDecimal.ZERO;

        // thousands separators
        cleaned = cleaned.replace(".", "");
        // decimal comma
        cleaned = cleaned.replace(",", ".");
        // keep only valid chars
        cleaned = cleaned.replaceAll("[^0-9\\.-]", "");

        if (cleaned.isEmpty() || cleaned.equals("-") || cleaned.equals(".")) return BigDecimal.ZERO;
        return new BigDecimal(cleaned);
    }

    private static String normalizeForSearch(String text) {
        if (text == null) return "";
        String n = text;
        n = n.replace('\u00A0', ' ');
        n = Normalizer.normalize(n, Normalizer.Form.NFD);
        n = n.replaceAll("\\p{M}", "");
        n = n.toLowerCase(Locale.ROOT);
        return n;
    }

    private static String normalizeSpaces(String text) {
        if (text == null) return "";
        return text.replaceAll("\\s+", " ").trim();
    }

    private static String stripLeadingNonDigit(String text) {
        if (text == null) return "";
        return text.replaceFirst("^[^0-9]+", "").trim();
    }

    private static String stripLeadingNonAlnum(String text) {
        if (text == null) return "";
        return text.replaceFirst("^[^\\p{L}\\p{N}]+", "").trim();
    }

    private static Integer safeInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return null;
        }
    }

    private static Integer inferYearFromText(String text) {
        if (text == null || text.isBlank()) return null;
        // Prefer dd/MM/yyyy if present
        Matcher m = Pattern.compile("\\b\\d{2}/\\d{2}/(\\d{4})\\b").matcher(text);
        if (m.find()) {
            Integer y = safeInt(m.group(1));
            if (y != null && y >= 2000 && y <= 2100) return y;
        }
        return null;
    }

    private static String extractCategoryFromCategoryCityLine(String line) {
        if (line == null) return null;
        String s = line.trim();
        if (s.isEmpty()) return null;
        // e.g. "SAÚDE.FORTALEZA" => "Saúde"
        int idx = s.indexOf('.');
        String cat = (idx > 0 ? s.substring(0, idx) : s).trim();
        if (cat.isEmpty()) return null;
        // keep original case but normalize spaces
        return normalizeSpaces(cat);
    }
}
