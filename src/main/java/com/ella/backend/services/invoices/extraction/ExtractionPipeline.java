package com.ella.backend.services.invoices.extraction;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import com.ella.backend.config.QualityScoreConfig;
import com.ella.backend.services.invoices.InvoiceParsingException;
import com.ella.backend.services.invoices.parsers.BancoDoBrasilInvoiceParser;
import com.ella.backend.services.invoices.parsers.BradescoInvoiceParser;
import com.ella.backend.services.invoices.parsers.C6InvoiceParser;
import com.ella.backend.services.invoices.parsers.InvoiceParserFactory;
import com.ella.backend.services.invoices.parsers.InvoiceParserSelector;
import com.ella.backend.services.invoices.parsers.InvoiceParserStrategy;
import com.ella.backend.services.invoices.parsers.ItauInvoiceParser;
import com.ella.backend.services.invoices.parsers.NubankInvoiceParser;
import com.ella.backend.services.invoices.parsers.ParseResult;
import com.ella.backend.services.invoices.parsers.PdfAwareInvoiceParser;
import com.ella.backend.services.invoices.parsers.SantanderInvoiceParser;
import com.ella.backend.services.invoices.parsers.TransactionData;
import com.ella.backend.services.invoices.quality.ParseQualityEvaluator;
import com.ella.backend.services.invoices.quality.ParseQualityValidator;
import com.ella.backend.services.ocr.OcrException;
import com.ella.backend.services.ocr.OcrProperties;
import com.ella.backend.services.ocr.PdfOcrExtractor;
import com.ella.backend.services.ocr.PdfTextExtractor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExtractionPipeline {

    private static final org.slf4j.Logger STATIC_LOG = org.slf4j.LoggerFactory.getLogger(ExtractionPipeline.class);

    // Keep marker stable to ease prod debugging.
    private static final String BUILD_MARKER = "2026-01-02T0826";

    private final InvoiceParserFactory invoiceParserFactory;
    private final PdfTextExtractor pdfTextExtractor;
    private final PdfOcrExtractor pdfOcrExtractor;
    private final OcrProperties ocrProperties;
    private final Environment environment;
    private final ParseQualityEvaluator parseQualityEvaluator;
    private final QualityScoreConfig qualityScoreConfig;
    private final ParseQualityValidator parseQualityValidator;

    private final AdobeExtractor adobeExtractor;
    private final AdobeFallbackStrategy adobeFallbackStrategy;

    private final AtomicBoolean debugConfigLoggedOnce = new AtomicBoolean(false);

    @Value("${ella.invoice.debug.log-extracted-text:${ella.invoice.debug.log.extracted.text:false}}")
    private boolean logExtractedText;

    @Value("${ella.invoice.debug.extracted-text-max-chars:${ella.invoice.debug.extracted.text.max.chars:2000}}")
    private int extractedTextMaxChars;

    @Value("${ella.invoice.debug.due-date-snippets:${ella.invoice.debug.due.date.snippets:false}}")
    private boolean logDueDateSnippets;

    @Value("${ella.invoice.debug.due-date-context-chars:${ella.invoice.debug.due.date.context.chars:140}}")
    private int dueDateContextChars;

    public ExtractionResult extractFromPdf(InputStream inputStream, String password, String dueDateOverride) throws IOException {
        byte[] pdfBytes = inputStream.readAllBytes();
        return extractFromPdfBytes(pdfBytes, password, dueDateOverride);
    }

    public ExtractionResult extractFromPdfBytes(byte[] pdfBytes, String password, String dueDateOverride) throws IOException {
        forceDebugHeader("extractFromPdfBytes");
        logDebugConfigOnce("extractFromPdfBytes");

        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new IllegalArgumentException("PDF vazio (0 bytes)");
        }

        LocalDate dueDateFromRequest = null;
        if (dueDateOverride != null && !dueDateOverride.isBlank()) {
            dueDateFromRequest = parseDueDateOverrideOrThrow(dueDateOverride);
        }

        log.info("[InvoiceUpload][PDF] Read pdfBytes={} bytes", pdfBytes.length);

        try (PDDocument document = (password != null && !password.isBlank())
                ? PDDocument.load(new ByteArrayInputStream(pdfBytes), password)
                : PDDocument.load(new ByteArrayInputStream(pdfBytes))) {
            try {
                document.setAllSecurityToBeRemoved(true);
            } catch (Exception ignored) {
            }

            String text = pdfTextExtractor.extractText(document);
            text = text == null ? "" : text;

            // Safe removal: Mercado Pago invoices are intentionally not supported.
            // Detect early (before baseline parser selection) to avoid misleading parser/due-date errors.
            if (looksLikeMercadoPagoInvoice(text)) {
                throw new com.ella.backend.services.invoices.InvoiceParsingException(
                        "Ainda não suportamos faturas do Mercado Pago. " +
                                "Envie a fatura de outro banco/cartão ou tente novamente mais tarde."
                );
            }

            // Identifica o parser baseado no texto do PDFBox para decidir políticas específicas.
            InvoiceParserStrategy baselineParser = null;
            try {
                InvoiceParserSelector.Selection selection = InvoiceParserSelector.selectBest(invoiceParserFactory.getParsers(), text);
                baselineParser = selection != null && selection.chosen() != null ? selection.chosen().parser() : null;
            } catch (Exception ignored) {
                baselineParser = null;
            }
            boolean skipOcrForItauC6NubankBbSantander = baselineParser instanceof ItauInvoiceParser
                    || baselineParser instanceof C6InvoiceParser
                    || baselineParser instanceof NubankInvoiceParser
                    || baselineParser instanceof BancoDoBrasilInvoiceParser
                    || baselineParser instanceof BradescoInvoiceParser
                    || baselineParser instanceof SantanderInvoiceParser;

            logExtractedTextIfEnabled("PDFBox", text);
            logDueDateSignalsIfEnabled("PDFBox", text);

            boolean ocrAttempted = false;
            if (shouldAttemptOcr(text)) {
                if (skipOcrForItauC6NubankBbSantander) {
                    log.info("[InvoiceUpload][OCR] Skipping OCR for Itau/C6/Nubank/BB/Bradesco/Santander (disabled for these parsers)");
                } else {
                    text = runOcrOrThrow(document);
                    ocrAttempted = true;

                    logExtractedTextIfEnabled("OCR", text);
                    logDueDateSignalsIfEnabled("OCR", text);
                }
            }

            if (text.isBlank()) {
                return new ExtractionResult(ParseResult.builder().transactions(List.of()).build(), text, "PDFBox", ocrAttempted);
            }

            log.info("[InvoiceUpload][BUILD_MARKER={}] PDF extracted sample: {}",
                    BUILD_MARKER,
                    (text.length() > 500 ? text.substring(0, 500) : text));

            try {
                ParseResult parseResult = parsePdfText(pdfBytes, text, dueDateFromRequest);
                List<TransactionData> transactions = parseResult.getTransactions();

                // Diagnostic: if the invoice total is present in the extracted text, log the comparison once.
                if (transactions != null && !transactions.isEmpty()) {
                    BigDecimal expectedTotal = extractInvoiceExpectedTotal(text);
                    if (expectedTotal != null && expectedTotal.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal extractedTotal = sumExpenseAmounts(transactions);
                        BigDecimal pct = extractedTotal.compareTo(BigDecimal.ZERO) > 0
                                ? extractedTotal.multiply(BigDecimal.valueOf(100))
                                    .divide(expectedTotal, 2, java.math.RoundingMode.HALF_UP)
                                : BigDecimal.ZERO;
                        STATIC_LOG.info(
                                "[InvoiceUpload][OCR] Pre-check: ocrAttempted={} txCount={} extracted={} expected={} ({}% of expected)",
                                ocrAttempted, transactions.size(), extractedTotal, expectedTotal, pct);
                    }
                }

                if ((transactions == null || transactions.isEmpty()) && !ocrAttempted && ocrProperties.isEnabled()) {
                    if (skipOcrForItauC6NubankBbSantander) {
                        log.info("[InvoiceUpload][OCR] Skipping OCR empty-result retry for Itau/C6/Nubank/BB/Bradesco/Santander (disabled for these parsers)");
                    } else {
                        String ocrText = runOcrOrThrow(document);
                        parseResult = parsePdfText(pdfBytes, ocrText, dueDateFromRequest);
                        transactions = parseResult.getTransactions();
                        ocrAttempted = true;
                        text = ocrText;
                    }
                }

                // Some PDFs contain selectable text but with broken font encoding, producing "garbled" merchants.
                if (transactions != null && !transactions.isEmpty() && !ocrAttempted && ocrProperties.isEnabled()
                        && shouldRetryWithOcrForQuality(transactions)) {
                    if (skipOcrForItauC6NubankBbSantander) {
                        log.info("[InvoiceUpload][OCR] Skipping OCR quality retry for Itau/C6/Nubank/BB/Bradesco/Santander (disabled for these parsers)");
                    } else {
                        log.info("[OCR] Trigger: parsed transactions look garbled; retrying once with OCR...");
                        String ocrText = runOcrOrThrow(document);
                        ocrAttempted = true;
                        ParseResult ocrParseResult = parsePdfText(pdfBytes, ocrText, dueDateFromRequest);
                        List<TransactionData> ocrTransactions = ocrParseResult.getTransactions();
                        if (isOcrResultBetter(ocrTransactions, transactions)) {
                            logInvoiceTotalValidation("OCR", ocrText, ocrTransactions);
                            return finalizeResultWithAdobeFallback(ocrParseResult, ocrText, "OCR", true, pdfBytes, dueDateFromRequest);
                        }
                    }
                }

                // Detect missing transactions by comparing extracted total vs. the invoice total shown on the PDF.
                if (transactions != null && !transactions.isEmpty() && !ocrAttempted && ocrProperties.isEnabled()
                        && shouldRetryDueToMissingTransactions(transactions, text)) {
                    if (skipOcrForItauC6NubankBbSantander) {
                        log.info("[InvoiceUpload][OCR] Skipping OCR missing-transactions retry for Itau/C6/Nubank/BB/Bradesco/Santander (disabled for these parsers)");

                        // Non-OCR fallback: retry PDFBox extraction with positional sorting.
                        try {
                            String sortedText = pdfTextExtractor.extractTextSorted(document);
                            if (sortedText != null && !sortedText.isBlank()) {
                                ParseResult sortedParseResult = parsePdfText(pdfBytes, sortedText, dueDateFromRequest);
                                List<TransactionData> sortedTransactions = sortedParseResult.getTransactions();

                                BigDecimal expected = extractInvoiceExpectedTotal(text);
                                if (expected == null) {
                                    expected = extractInvoiceExpectedTotal(sortedText);
                                }

                                if (expected != null
                                        && isOcrResultBetterForMissingTransactions(sortedTransactions, transactions, expected)) {
                                    logInvoiceTotalValidation("PDFBox-sorted", sortedText, sortedTransactions);
                                    return finalizeResultWithAdobeFallback(sortedParseResult, sortedText, "PDFBox-sorted", false, pdfBytes, dueDateFromRequest);
                                }
                            }
                        } catch (Exception e) {
                            log.info("[InvoiceUpload][PDFBox] Sorted extraction retry failed: {}", e.getMessage());
                        }
                    } else {
                        log.info("[OCR] Trigger: possible missing transactions (total mismatch); retrying once with OCR...");
                        String ocrText = runOcrOrThrow(document);
                        ocrAttempted = true;
                        ParseResult ocrParseResult = parsePdfText(pdfBytes, ocrText, dueDateFromRequest);
                        List<TransactionData> ocrTransactions = ocrParseResult.getTransactions();

                        BigDecimal expected = extractInvoiceExpectedTotal(text);
                        if (expected == null) {
                            expected = extractInvoiceExpectedTotal(ocrText);
                        }

                        if (expected != null && isOcrResultBetterForMissingTransactions(ocrTransactions, transactions, expected)) {
                            logInvoiceTotalValidation("OCR", ocrText, ocrTransactions);
                            return finalizeResultWithAdobeFallback(ocrParseResult, ocrText, "OCR", true, pdfBytes, dueDateFromRequest);
                        }
                    }
                }

                String source = ocrAttempted ? "OCR" : "PDFBox";
                logInvoiceTotalValidation(source, text, transactions);
                return finalizeResultWithAdobeFallback(parseResult, text, source, ocrAttempted, pdfBytes, dueDateFromRequest);
            } catch (IllegalArgumentException e) {
                // If parsing fails (missing due date / unsupported layout / etc), retry once with OCR when enabled.
                if (!ocrAttempted && ocrProperties.isEnabled()) {
                    if (skipOcrForItauC6NubankBbSantander) {
                        log.warn("[InvoiceUpload][OCR] Skipping OCR exception retry for Itau/C6/Nubank/BB/Bradesco/Santander (disabled for these parsers): {}", e.getMessage());
                        throw e;
                    }
                    log.warn("[OCR] Parsing failed ({}). Retrying once with OCR...", e.getMessage());
                    ocrAttempted = true;
                    String ocrText = runOcrOrThrow(document);
                    logDueDateSignalsIfEnabled("OCR-retry", ocrText);
                    ParseResult ocrParseResult = parsePdfText(pdfBytes, ocrText, dueDateFromRequest);
                    List<TransactionData> parsed = ocrParseResult.getTransactions();
                    logInvoiceTotalValidation("OCR", ocrText, parsed);
                    return finalizeResultWithAdobeFallback(ocrParseResult, ocrText, "OCR", true, pdfBytes, dueDateFromRequest);
                }
                throw e;
            }
        } catch (org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException e) {
            if (password != null && !password.isBlank()) {
                throw new IllegalArgumentException("Senha incorreta para o arquivo PDF.");
            }
            throw new IllegalArgumentException("O arquivo PDF está protegido por senha. Por favor, forneça a senha.");
        }
    }

    private ParseResult applyQualityScore(ParseResult parseResult, String rawText, String source) {
        if (parseResult == null) {
            return ParseResult.builder().transactions(List.of()).build();
        }

        parseResult.setSource(source);
        parseResult.setRawText(rawText);

        int qualityScore = parseQualityEvaluator.evaluate(parseResult, rawText);
        parseResult.setQualityScore(qualityScore);

        log.info("[InvoiceUpload] Extraction result: {}", parseResult.getDescription());
        log.info("[InvoiceUpload] Quality score: {}", parseResult.getQualityScore());
        if (qualityScoreConfig != null) {
            log.info("[InvoiceUpload] Quality config: {}", qualityScoreConfig.getDescription());
        }

        return parseResult;
    }

    private void validateOrThrow(ParseResult parseResult) {
        if (parseQualityValidator == null || qualityScoreConfig == null) {
            throw new InvoiceParsingException("Extraction validation not configured (validator/config is null)");
        }

        if (!parseQualityValidator.isValid(parseResult, qualityScoreConfig)) {
            String reason = parseQualityValidator.getRejectReason(parseResult, qualityScoreConfig);
            log.warn("[InvoiceUpload] Extraction REJECTED: {}", reason);
            throw new InvoiceParsingException("Extraction failed validation: " + reason);
        }

        log.info("[InvoiceUpload] Extraction ACCEPTED (score: {})", parseResult.getQualityScore());
    }

    private ExtractionResult finalizeResultWithAdobeFallback(
            ParseResult baseParseResult,
            String baseRawText,
            String baseParseSource,
            boolean ocrAttempted,
            byte[] pdfBytes,
            LocalDate dueDateFromRequest
    ) {
        ParseResult current = applyQualityScore(baseParseResult, baseRawText, baseParseSource);
        int currentScore = current.getQualityScore();

        String topLevelSource = "PDFBox";
        String fallbackDecision = null;
        ParseResult chosen = current;
        String chosenText = baseRawText;

        boolean shouldTryAdobe = adobeFallbackStrategy != null
                && adobeFallbackStrategy.shouldTryAdobeFallback(currentScore);

        if (shouldTryAdobe && adobeExtractor != null) {
            log.info("[ExtractionPipeline] Tentando fallback Adobe (score atual: {})", currentScore);

            String adobeText = adobeExtractor.extract(pdfBytes);
            ParseResult adobeParse = null;
            int adobeScore = 0;

            if (adobeText != null && !adobeText.isBlank()) {
                try {
                    adobeParse = parsePdfText(pdfBytes, adobeText, dueDateFromRequest);
                    adobeParse = applyQualityScore(adobeParse, adobeText, "Adobe");
                    adobeScore = adobeParse.getQualityScore();
                    log.info("[ExtractionPipeline] Adobe parsing concluído com score: {}", adobeScore);
                } catch (Exception e) {
                    log.warn("[ExtractionPipeline] Falha ao fazer parse do texto Adobe: {}", e.getMessage());
                    adobeParse = null;
                }
            } else {
                log.warn("[ExtractionPipeline] Adobe não retornou texto, mantendo resultado atual");
            }

            String decision = adobeFallbackStrategy.decideBestResult(current, adobeParse);
            fallbackDecision = decision;

            log.info(
                    "[ExtractionPipeline] Decisão de fallback: {}",
                    adobeFallbackStrategy.getDecisionDescription(decision, currentScore, adobeScore)
            );

            if (AdobeFallbackStrategy.DECISION_ADOBE.equals(decision) && adobeParse != null) {
                chosen = adobeParse;
                chosenText = adobeText;
                topLevelSource = "Adobe";
            }
        }

        validateOrThrow(chosen);
        return new ExtractionResult(chosen, chosenText, topLevelSource, ocrAttempted, fallbackDecision);
    }

    private void logInvoiceTotalValidation(String source, String text, List<TransactionData> transactions) {
        try {
            if (transactions == null || transactions.isEmpty()) return;

            BigDecimal expectedTotal = extractInvoiceExpectedTotal(text);
            if (expectedTotal == null || expectedTotal.compareTo(BigDecimal.ZERO) <= 0) return;

            BigDecimal extractedTotal = sumExpenseAmounts(transactions);
            BigDecimal difference = extractedTotal.subtract(expectedTotal).abs();

            log.info("[VALIDATION][{}] Total Extraído: R$ {}, Total Esperado: R$ {}, Diferença: R$ {}",
                    source, extractedTotal, expectedTotal, difference);

            if (difference.compareTo(new BigDecimal("0.01")) > 0) {
                log.warn("[VALIDATION][{}] A diferença entre o total extraído e o esperado é maior que 1 centavo!", source);
            }
        } catch (Exception ignored) {
        }
    }

    static boolean shouldRetryWithOcrForQuality(List<TransactionData> transactions) {
        if (transactions == null || transactions.isEmpty()) return false;

        int total = 0;
        int garbled = 0;
        int missingDate = 0;

        for (TransactionData tx : transactions) {
            if (tx == null) continue;
            total++;
            if (tx.date == null) missingDate++;
            if (isLikelyGarbledMerchant(tx.description)) garbled++;
        }

        if (total == 0) return false;

        boolean anyGarbledDescs = garbled >= 1;
        boolean manyMissingDates = missingDate >= Math.max(2, (int) Math.ceil(total * 0.5));
        boolean trigger = manyMissingDates || anyGarbledDescs;

        if (trigger) {
            StringBuilder sample = new StringBuilder();
            int shown = 0;
            for (TransactionData tx : transactions) {
                if (tx == null) continue;
                if (!isLikelyGarbledMerchant(tx.description) && tx.date != null) continue;
                if (shown >= 3) break;
                String desc = tx.description == null ? "" : tx.description;
                if (desc.length() > 60) desc = desc.substring(0, 60) + "...";
                sample.append('[').append(tx.date).append(']').append(desc).append(' ');
                shown++;
            }
            STATIC_LOG.info("[OCR] Quality trigger: total={} garbled={} missingDate={} samples={}", total, garbled, missingDate, sample.toString().trim());
        }

        return trigger;
    }

    private static final BigDecimal MISSING_TX_RATIO_THRESHOLD = new BigDecimal("0.96");

    static boolean shouldRetryDueToMissingTransactions(List<TransactionData> transactions, String text) {
        if (transactions == null || transactions.isEmpty()) return false;

        BigDecimal expectedTotal = extractInvoiceExpectedTotal(text);
        if (expectedTotal == null || expectedTotal.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }

        BigDecimal totalExtracted = sumExpenseAmounts(transactions);
        if (totalExtracted.compareTo(BigDecimal.ZERO) <= 0) return false;

        BigDecimal threshold = expectedTotal.multiply(MISSING_TX_RATIO_THRESHOLD);
        boolean trigger = totalExtracted.compareTo(threshold) < 0;

        BigDecimal pct = totalExtracted.multiply(BigDecimal.valueOf(100))
            .divide(expectedTotal, 2, java.math.RoundingMode.HALF_UP);
        STATIC_LOG.info(
            "[InvoiceUpload][OCR] Total check: txCount={} extracted={} expected={} threshold={} ({}% of expected) trigger={}",
            transactions.size(), totalExtracted, expectedTotal, threshold, pct, trigger);

        return trigger;
    }

    private static boolean isOcrResultBetterForMissingTransactions(
            List<TransactionData> ocrTransactions,
            List<TransactionData> originalTransactions,
            BigDecimal expectedTotal
    ) {
        if (expectedTotal == null || expectedTotal.compareTo(BigDecimal.ZERO) <= 0) return false;

        BigDecimal origTotal = sumExpenseAmounts(originalTransactions);
        BigDecimal ocrTotal = sumExpenseAmounts(ocrTransactions);

        BigDecimal origDiff = expectedTotal.subtract(origTotal).abs();
        BigDecimal ocrDiff = expectedTotal.subtract(ocrTotal).abs();

        int origCount = originalTransactions == null ? 0 : originalTransactions.size();
        int ocrCount = ocrTransactions == null ? 0 : ocrTransactions.size();

        boolean better = false;

        if (ocrCount >= origCount && ocrDiff.compareTo(origDiff) < 0) {
            better = true;
        }

        if (!better && ocrCount > origCount && ocrDiff.compareTo(origDiff) <= 0) {
            better = true;
        }

        STATIC_LOG.info("[InvoiceUpload][OCR] OCR retry evaluated: txCount {} -> {} | extracted {} -> {} | diffToExpected {} -> {} | accepted={} ",
                origCount, ocrCount, origTotal, ocrTotal, origDiff, ocrDiff, better);

        return better;
    }

    private static BigDecimal sumExpenseAmounts(List<TransactionData> transactions) {
        if (transactions == null || transactions.isEmpty()) return BigDecimal.ZERO;
        BigDecimal sum = BigDecimal.ZERO;
        for (TransactionData tx : transactions) {
            if (tx == null || tx.amount == null) continue;
            if (tx.type != null && tx.type != com.ella.backend.enums.TransactionType.EXPENSE) continue;
            sum = sum.add(tx.amount.abs());
        }
        return sum;
    }

    private static BigDecimal sumNetAmounts(List<TransactionData> transactions) {
        if (transactions == null || transactions.isEmpty()) return BigDecimal.ZERO;
        BigDecimal sum = BigDecimal.ZERO;
        for (TransactionData tx : transactions) {
            if (tx == null || tx.amount == null || tx.type == null) continue;

            if (tx.type == com.ella.backend.enums.TransactionType.EXPENSE) {
                sum = sum.add(tx.amount.abs());
            } else if (tx.type == com.ella.backend.enums.TransactionType.INCOME) {
                sum = sum.subtract(tx.amount.abs());
            }
        }
        return sum;
    }

    private static BigDecimal extractInvoiceExpectedTotal(String text) {
        if (text == null || text.isBlank()) return null;

        String normalized = text.replace('\u00A0', ' ');

        List<Pattern> patterns = List.of(
            // Itaú: o PDF costuma ter "Total da fatura anterior" + "Total desta fatura".
            // Precisamos priorizar a fatura atual e NÃO capturar a anterior.
            Pattern.compile("(?is)\\btotal\\s+desta\\s+fatura\\b[^0-9]{0,25}R?\\$?\\s*([0-9][0-9\\s\\.,]{0,25}[0-9])"),
            Pattern.compile("(?is)\\blan[cç]amentos\\s+atuais\\b[^0-9]{0,25}R?\\$?\\s*([0-9][0-9\\s\\.,]{0,25}[0-9])"),
            Pattern.compile("(?is)\\btotal\\s+dos\\s+lan[cç]amentos\\s+atuais\\b[^0-9]{0,25}R?\\$?\\s*([0-9][0-9\\s\\.,]{0,25}[0-9])"),

            // Genérico (todos os bancos): NÃO capturar "fatura anterior".
            Pattern.compile("(?is)\\btotal\\s+(?:da\\s+)?fatura\\b(?!\\s*anterior)[^0-9]{0,25}R?\\$?\\s*([0-9][0-9\\s\\.,]{0,25}[0-9])"),
                Pattern.compile("(?is)\\btotal\\s+a\\s+pagar\\b[^0-9]{0,25}R?\\$?\\s*([0-9][0-9\\s\\.,]{0,25}[0-9])"),
                Pattern.compile("(?is)\\bvalor\\s+total\\b[^0-9]{0,25}R?\\$?\\s*([0-9][0-9\\s\\.,]{0,25}[0-9])")
        );

        for (Pattern p : patterns) {
            Matcher m = p.matcher(normalized);
            if (!m.find()) continue;
            BigDecimal v = parseBrlAmountLoose(m.group(1));
            if (v != null && v.compareTo(BigDecimal.ZERO) > 0) {
                return v;
            }
        }

        return null;
    }

    private static BigDecimal parseBrlAmountLoose(String raw) {
        if (raw == null) return null;
        String s = raw.trim().replace("R$", "").replace(" ", "");
        s = s.replaceAll("[^0-9,\\.]", "");
        if (s.isEmpty()) return null;

        if (s.contains(",")) {
            s = s.replace(".", "").replace(",", ".");
        } else {
            if (!s.matches("-?\\d+\\.\\d{2}")) {
                s = s.replace(".", "");
            }
        }

        try {
            return new BigDecimal(s);
        } catch (Exception e) {
            return null;
        }
    }

    static boolean isOcrResultBetter(List<TransactionData> ocrResult, List<TransactionData> original) {
        int ocrScore = qualityScore(ocrResult);
        int origScore = qualityScore(original);
        return ocrScore > origScore;
    }

    private static int qualityScore(List<TransactionData> txs) {
        if (txs == null || txs.isEmpty()) return 0;
        int total = 0;
        int garbled = 0;
        int missingDate = 0;
        for (TransactionData tx : txs) {
            if (tx == null) continue;
            total++;
            if (tx.date == null) missingDate++;
            if (isLikelyGarbledMerchant(tx.description)) garbled++;
        }
        if (total == 0) return 0;

        int score = total * 1000;
        score -= garbled * 250;
        score -= missingDate * 250;
        return score;
    }

    static boolean isLikelyGarbledMerchant(String description) {
        if (description == null) return false;
        String d = description.trim();
        if (d.isEmpty()) return false;

        String upper = d.toUpperCase(java.util.Locale.ROOT);
        if (upper.contains("UBER") || upper.contains("IFOOD") || upper.contains("PAGAMENTO") || upper.contains("ANUIDADE")) {
            return false;
        }

        int letters = 0;
        int digits = 0;
        int vowels = 0;
        int longAlnumTokens = 0;
        int mixedLetterDigitTokens = 0;

        for (String token : d.split("\\s+")) {
            String t = token.replaceAll("[^A-Za-z0-9]", "");
            if (t.length() < 8) continue;

            boolean hasLetter = t.matches(".*[A-Za-z].*");
            boolean hasDigit = t.matches(".*\\d.*");
            if (t.length() >= 10 && t.matches("[A-Za-z0-9]+")) {
                longAlnumTokens++;
            }
            if (t.length() >= 10 && hasLetter && hasDigit && countDigits(t) >= 2) {
                mixedLetterDigitTokens++;
            }
        }

        for (int i = 0; i < upper.length(); i++) {
            char c = upper.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                letters++;
                if (c == 'A' || c == 'E' || c == 'I' || c == 'O' || c == 'U') vowels++;
            } else if (c >= '0' && c <= '9') {
                digits++;
            }
        }

        if (mixedLetterDigitTokens >= 1) {
            return true;
        }

        if (longAlnumTokens >= 1 && letters >= 8 && digits >= 2 && vowels <= 1) {
            return true;
        }
        if (letters >= 12 && vowels == 0 && longAlnumTokens >= 2) {
            return true;
        }

        return false;
    }

    private static int countDigits(String value) {
        if (value == null || value.isEmpty()) return 0;
        int count = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= '0' && c <= '9') count++;
        }
        return count;
    }

    private void logDebugConfigOnce(String reason) {
        if (!debugConfigLoggedOnce.compareAndSet(false, true)) return;
        logDebugConfigSnapshot(reason);
    }

    private void logDebugConfigSnapshot(String reason) {
        String envDueSnip = System.getenv("ELLA_INVOICE_DEBUG_DUE_DATE_SNIPPETS");
        String envDueCtx = System.getenv("ELLA_INVOICE_DEBUG_DUE_DATE_CONTEXT_CHARS");
        String envLogText = System.getenv("ELLA_INVOICE_DEBUG_LOG_EXTRACTED_TEXT");
        String envLogMax = System.getenv("ELLA_INVOICE_DEBUG_EXTRACTED_TEXT_MAX_CHARS");
        String envProfiles = System.getenv("SPRING_PROFILES_ACTIVE");

        log.info("[InvoiceUpload] Debug snapshot reason={} activeProfiles={} SPRING_PROFILES_ACTIVE='{}'",
                reason,
                Arrays.toString(environment.getActiveProfiles()),
                envProfiles);

        log.info("[InvoiceUpload] Debug flags (injected): logExtractedText={} extractedTextMaxChars={} dueDateSnippets={} dueDateContextChars={} ",
                logExtractedText, extractedTextMaxChars, logDueDateSnippets, dueDateContextChars);

        log.info("[InvoiceUpload] Debug env vars: ELLA_INVOICE_DEBUG_DUE_DATE_SNIPPETS='{}' ELLA_INVOICE_DEBUG_DUE_DATE_CONTEXT_CHARS='{}' ELLA_INVOICE_DEBUG_LOG_EXTRACTED_TEXT='{}' ELLA_INVOICE_DEBUG_EXTRACTED_TEXT_MAX_CHARS='{}'",
                envDueSnip, envDueCtx, envLogText, envLogMax);

        log.info("[InvoiceUpload] Build marker={}", BUILD_MARKER);

        log.info("[InvoiceUpload] Debug props (Environment): due-date-snippets='{}' due.date.snippets='{}' due-date-context-chars='{}' due.date.context.chars='{}' log-extracted-text='{}' log.extracted.text='{}' extracted-text-max-chars='{}' extracted.text.max.chars='{}'",
            environment.getProperty("ella.invoice.debug.due-date-snippets"),
            environment.getProperty("ella.invoice.debug.due.date.snippets"),
            environment.getProperty("ella.invoice.debug.due-date-context-chars"),
            environment.getProperty("ella.invoice.debug.due.date.context.chars"),
            environment.getProperty("ella.invoice.debug.log-extracted-text"),
            environment.getProperty("ella.invoice.debug.log.extracted.text"),
            environment.getProperty("ella.invoice.debug.extracted-text-max-chars"),
            environment.getProperty("ella.invoice.debug.extracted.text.max.chars"));
    }

    private void forceDebugHeader(String where) {
        try {
            System.err.println("=== INVOICE UPLOAD DEBUG HEADER ===");
            System.err.println("where: " + where);
            System.err.println("buildMarker: " + BUILD_MARKER);
            System.err.println("activeProfiles: " + Arrays.toString(environment.getActiveProfiles()));
            System.err.println("SPRING_PROFILES_ACTIVE: " + System.getenv("SPRING_PROFILES_ACTIVE"));
            System.err.println("ELLA_INVOICE_DEBUG_DUE_DATE_SNIPPETS: " + System.getenv("ELLA_INVOICE_DEBUG_DUE_DATE_SNIPPETS"));
            System.err.println("ELLA_INVOICE_DEBUG_DUE_DATE_CONTEXT_CHARS: " + System.getenv("ELLA_INVOICE_DEBUG_DUE_DATE_CONTEXT_CHARS"));
            System.err.println("ELLA_INVOICE_DEBUG_LOG_EXTRACTED_TEXT: " + System.getenv("ELLA_INVOICE_DEBUG_LOG_EXTRACTED_TEXT"));
            System.err.println("ELLA_INVOICE_DEBUG_EXTRACTED_TEXT_MAX_CHARS: " + System.getenv("ELLA_INVOICE_DEBUG_EXTRACTED_TEXT_MAX_CHARS"));

            System.err.println("prop ella.invoice.debug.due-date-snippets: " + environment.getProperty("ella.invoice.debug.due-date-snippets"));
            System.err.println("prop ella.invoice.debug.due.date.snippets: " + environment.getProperty("ella.invoice.debug.due.date.snippets"));
            System.err.println("prop ella.invoice.debug.due-date-context-chars: " + environment.getProperty("ella.invoice.debug.due-date-context-chars"));
            System.err.println("prop ella.invoice.debug.due.date.context.chars: " + environment.getProperty("ella.invoice.debug.due.date.context.chars"));

            System.err.println("injected dueDateSnippets: " + logDueDateSnippets);
            System.err.println("injected dueDateContextChars: " + dueDateContextChars);
            System.err.println("injected logExtractedText: " + logExtractedText);
            System.err.println("injected extractedTextMaxChars: " + extractedTextMaxChars);
            System.err.println("=== /INVOICE UPLOAD DEBUG HEADER ===");
        } catch (Exception ignored) {
        }
    }

    private void logExtractedTextIfEnabled(String source, String text) {
        if (!logExtractedText) return;
        String t = text == null ? "" : text;

        String payload = t;
        int max = extractedTextMaxChars;
        if (max > 0 && t.length() > max) {
            payload = t.substring(0, max) + "\n... (truncated)";
        }

        log.info("[InvoiceUpload][DEBUG] Extracted text source={} len={} maxChars={}\n--BEGIN--\n{}\n--END--",
                source,
                t.length(),
                max,
                payload
        );
    }

    private void logDueDateSignalsIfEnabled(String source, String text) {
        if (!logDueDateSnippets) return;

        String t = text == null ? "" : text;
        String normalized = normalizeNumericDates(t);

        boolean hasKeyword = Pattern.compile("(?is)\\b(venc(?:imento)?|vct(?:o)?|data\\s+de\\s+vencimento)\\b").matcher(normalized).find();
        boolean hasDdMmYyyy = Pattern.compile("(?s)\\b\\d{2}/\\d{2}/\\d{4}\\b").matcher(normalized).find();
        boolean hasDdMm = Pattern.compile("(?s)\\b\\d{2}/\\d{2}\\b").matcher(normalized).find();

        log.info("[InvoiceUpload][DEBUG] DueDate signals source={} len={} hasKeyword={} hasDdMmYyyy={} hasDdMm={}",
                source, normalized.length(), hasKeyword, hasDdMmYyyy, hasDdMm);

        if (!hasKeyword) return;

        Matcher m = Pattern.compile("(?is)\\b(venc(?:imento)?|vct(?:o)?|data\\s+de\\s+vencimento)\\b").matcher(normalized);
        if (!m.find()) return;

        int start = Math.max(0, m.start() - Math.max(0, dueDateContextChars));
        int end = Math.min(normalized.length(), m.end() + Math.max(0, dueDateContextChars));
        String snippet = normalized.substring(start, end);
        log.info("[InvoiceUpload][DEBUG] DueDate keyword snippet source={}\n--SNIP--\n{}\n--/SNIP--", source, snippet);
    }

    private boolean shouldAttemptOcr(String extractedText) {
        if (!ocrProperties.isEnabled()) return false;

        String t = extractedText == null ? "" : extractedText;
        if (t.isBlank()) {
            log.info("[OCR] Trigger: extracted text is blank");
            return true;
        }

        int minLen = Math.max(0, ocrProperties.getPdf().getMinTextLength());

        int nonWhitespace = 0;
        int alnum = 0;
        int replacement = 0;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (!Character.isWhitespace(c)) nonWhitespace++;
            if (Character.isLetterOrDigit(c)) alnum++;
            if (c == '\uFFFD') replacement++;
        }

        boolean tooLittleSignal = nonWhitespace < minLen || alnum < Math.max(40, minLen / 2);
        boolean tooGarbled = replacement > 10;

        if (tooLittleSignal || tooGarbled) {
            log.info("[OCR] Trigger: enabled=true textLen={} nonWs={} alnum={} minTextLen={} replacement={}",
                    t.length(), nonWhitespace, alnum, minLen, replacement);
            return true;
        }

        return false;
    }

    private String runOcrOrThrow(PDDocument document) {
        try {
            log.info("[OCR] Attempting OCR fallback (enabled=true)");
            String ocrText = pdfOcrExtractor.extractText(document);
            return ocrText == null ? "" : ocrText;
        } catch (OcrException e) {
            throw new IllegalArgumentException(
                    "Falha ao aplicar OCR neste PDF. " +
                            "Verifique se o Tesseract está instalado e se a configuração está correta " +
                            "(ella.ocr.enabled, ella.ocr.language, ella.ocr.tessdata-path).",
                    e
            );
        }
    }

    private ParseResult parsePdfText(byte[] pdfBytes, String text, LocalDate dueDateFromRequest) {
        String t = text == null ? "" : text;

        // Safe removal: Mercado Pago invoices are intentionally not supported.
        // Detect them early to avoid false parser selection (e.g., Banco do Brasil) and confusing errors.
        if (looksLikeMercadoPagoInvoice(t)) {
            throw new com.ella.backend.services.invoices.InvoiceParsingException(
                    "Ainda não suportamos faturas do Mercado Pago. " +
                            "Envie a fatura de outro banco/cartão ou tente novamente mais tarde."
            );
        }

        InvoiceParserSelector.Selection selection = InvoiceParserSelector.selectBest(invoiceParserFactory.getParsers(), t);
        InvoiceParserSelector.Candidate chosen = selection.chosen();
        InvoiceParserStrategy parser = chosen.parser();

        LocalDate dueDate = chosen.dueDate();
        List<TransactionData> transactions = chosen.transactions();

        if (parser instanceof PdfAwareInvoiceParser pdfAware) {
            // Isolated branch: only for parsers that explicitly support PDF-aware parsing.
            try {
                ParseResult pdfParse = pdfAware.parseWithPdf(pdfBytes, t);
                if (pdfParse != null && pdfParse.getDueDate() != null) {
                    dueDate = pdfParse.getDueDate();
                }
                if (pdfParse != null && pdfParse.getTransactions() != null && !pdfParse.getTransactions().isEmpty()) {
                    transactions = pdfParse.getTransactions();
                }
            } catch (Exception e) {
                log.warn("[InvoiceUpload] Pdf-aware parse failed, falling back to text parser. reason={}", e.toString());
            }
        }
        if (dueDate == null) {
            log.warn("[InvoiceUpload] Parser={} matched but dueDate was not found by parser. Trying fallback extractor...",
                    parser.getClass().getSimpleName());
            dueDate = tryExtractDueDateFallback(t);
        }

        if (dueDate == null && dueDateFromRequest != null) {
            log.warn("[InvoiceUpload] Using dueDate override from request: {}", dueDateFromRequest);
            dueDate = dueDateFromRequest;
        }
        if (dueDate == null) {
            throw new IllegalArgumentException(
                    "Não foi possível determinar a data de vencimento da fatura. " +
                            "O processamento foi interrompido para evitar lançamentos incorretos. " +
                            "(parser=" + parser.getClass().getSimpleName() + ")"
            );
        }

        transactions = transactions != null ? transactions : parser.extractTransactions(t);
        for (TransactionData tx : transactions) {
            if (tx == null) continue;
            tx.setDueDate(dueDate);
        }

        if (parser instanceof ItauInvoiceParser && dueDate != null && transactions != null && !transactions.isEmpty()) {
            int before = transactions.size();
            int dropped = 0;
            List<TransactionData> filtered = new ArrayList<>(transactions.size());
            for (TransactionData tx : transactions) {
                if (tx == null) {
                    filtered.add(null);
                    continue;
                }
                if (tx.type == com.ella.backend.enums.TransactionType.EXPENSE && tx.date != null && tx.date.isAfter(dueDate)) {
                    dropped++;
                    continue;
                }
                filtered.add(tx);
            }
            if (dropped > 0) {
                log.info("[InvoiceUpload][Itau] Dropped {} EXPENSE rows after dueDate={} ({} -> {})", dropped, dueDate, before, before - dropped);
            }
            transactions = filtered;
        }

        int garbled = 0;
        int missingDate = 0;
        List<String> sample = new ArrayList<>();
        for (TransactionData tx : transactions) {
            if (tx == null) continue;
            if (tx.date == null) missingDate++;
            if (isLikelyGarbledMerchant(tx.description)) {
                garbled++;
                if (sample.size() < 3 && tx.description != null) {
                    String s = tx.description.trim();
                    sample.add(s.length() > 60 ? s.substring(0, 60) : s);
                }
            }
        }

        log.info("[InvoiceUpload] Using parser={} dueDate={} txCount={}",
                parser.getClass().getSimpleName(), dueDate, transactions.size());
        log.info("[InvoiceUpload] Parse quality: txCount={} garbled={} missingDate={} garbledSamples={}",
                transactions.size(), garbled, missingDate, sample);

        BigDecimal totalAmount = extractInvoiceExpectedTotal(t);
        if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            if (parser instanceof SantanderInvoiceParser) {
                totalAmount = sumNetAmounts(transactions);
            } else {
                totalAmount = sumExpenseAmounts(transactions);
            }
        }

        return ParseResult.builder()
            .transactions(transactions)
            .dueDate(dueDate)
            .totalAmount(totalAmount)
            .bankName(parser.getClass().getSimpleName())
            .build();
    }

    private static boolean looksLikeMercadoPagoInvoice(String text) {
        if (text == null || text.isBlank()) return false;

        String n = com.ella.backend.services.invoices.util.NormalizeUtil.normalize(text)
            .replaceAll("\\s+", " ")
            .trim();

        // Avoid false positives from merchant lines like "MERCADOPAGO *XYZ" inside other banks.
        boolean hasBrand = n.contains("mercado pago") || n.contains("mercadopago");
        if (!hasBrand) return false;

        boolean hasInvoiceWording = n.contains("essa e sua fatura") || n.contains("esta e sua fatura") || n.contains("sua fatura");
        boolean hasDueWording = n.contains("vence em") || n.contains("vencimento");
        boolean hasTotalWording = n.contains("total a pagar") || (n.contains("total") && n.contains("pagar"));

        // Require at least 2 invoice-context markers in addition to the brand.
        int ctx = 0;
        if (hasInvoiceWording) ctx++;
        if (hasDueWording) ctx++;
        if (hasTotalWording) ctx++;

        return ctx >= 2;
    }

    private LocalDate parseDueDateOverrideOrThrow(String dueDate) {
        String v = dueDate == null ? "" : dueDate.trim();
        if (v.isEmpty()) return null;

        List<DateTimeFormatter> formatters = List.of(
                DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT)
        );

        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDate.parse(v, formatter);
            } catch (Exception ignored) {
            }
        }

        throw new IllegalArgumentException(
                "Formato inválido para dueDate. Use yyyy-MM-dd (ex: 2025-12-20) ou dd/MM/yyyy (ex: 20/12/2025)."
        );
    }

    private LocalDate tryExtractDueDateFallback(String text) {
        if (text == null || text.isBlank()) return null;

        String normalizedText = normalizeNumericDates(text);

        Integer inferredYear = inferYearFromText(normalizedText);

        var p1 = java.util.regex.Pattern.compile(
            "(?is)\\b(venc(?:imento)?|vct(?:o)?|data\\s+de\\s+vencimento)\\b[^\\d]{0,30}(\\d{2})\\s*[\\./-]\\s*(\\d{2})\\s*[\\./-]\\s*(\\d{4})");
        var m1 = p1.matcher(normalizedText);
        if (m1.find()) {
            Integer day = parseIntOrNull(m1.group(2));
            Integer month = parseIntOrNull(m1.group(3));
            Integer year = parseIntOrNull(m1.group(4));
            LocalDate d = safeDate(year, month, day);
            if (d != null) {
                log.warn("[InvoiceUpload] Fallback dueDate extracted via numeric pattern: {}", d);
                return d;
            }
        }

        var p1b = java.util.regex.Pattern.compile(
            "(?is)\\b(venc(?:imento)?|vct(?:o)?|data\\s+de\\s+vencimento)\\b[^\\d]{0,30}(\\d{2})\\s*[\\./-]\\s*(\\d{2})(?!\\s*[\\./-]\\s*\\d{4})");
        var m1b = p1b.matcher(normalizedText);
        if (m1b.find()) {
            Integer day = parseIntOrNull(m1b.group(2));
            Integer month = parseIntOrNull(m1b.group(3));
            Integer year = inferredYear != null ? inferredYear : LocalDate.now().getYear();
            LocalDate d = safeDate(year, month, day);
            if (d != null) {
                log.warn("[InvoiceUpload] Fallback dueDate extracted via numeric pattern (no year): {}", d);
                return d;
            }
        }

        var p2 = java.util.regex.Pattern.compile(
            "(?is)\\b(venc(?:imento)?|vct(?:o)?|data\\s+de\\s+vencimento|fatura)\\b[^\\d]{0,30}(\\d{2})\\s+([A-Z]{3})\\s+(\\d{4})");
        var m2 = p2.matcher(normalizedText);
        if (m2.find()) {
            Integer day = parseIntOrNull(m2.group(2));
            Integer month = monthAbbrevToNumber(m2.group(3));
            Integer year = parseIntOrNull(m2.group(4));
            LocalDate d = safeDate(year, month, day);
            if (d != null) {
                log.warn("[InvoiceUpload] Fallback dueDate extracted via textual pattern: {}", d);
                return d;
            }
        }

        try {
            var m3 = java.util.regex.Pattern.compile(
                    "(?is)\\b(venc(?:imento)?|vct(?:o)?|data\\s+de\\s+vencimento)\\b[^0-9]{0,60}([0-9][0-9\\s\\./-]{5,30})")
                    .matcher(text.replace('\u00A0', ' '));
            if (m3.find()) {
                String digits = m3.group(2).replaceAll("\\D", "");
                if (digits.length() >= 8) {
                    Integer day = parseIntOrNull(digits.substring(0, 2));
                    Integer month = parseIntOrNull(digits.substring(2, 4));
                    Integer year = parseIntOrNull(digits.substring(4, 8));
                    LocalDate d = safeDate(year, month, day);
                    if (d != null) {
                        log.warn("[InvoiceUpload] Fallback dueDate extracted via digits-only pattern: {}", d);
                        return d;
                    }
                }
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    private Integer inferYearFromText(String text) {
        try {
            if (text == null || text.isBlank()) return null;
            var m = java.util.regex.Pattern.compile("(?s)\\b\\d{2}\\s*/\\s*\\d{2}\\s*/\\s*(\\d{4})\\b").matcher(text);
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
        t = t.replaceAll("(?<=\\d)\\s+(?=\\d)", "");
        t = t.replaceAll("\\s*([\\./-])\\s*", "$1");
        return t;
    }

    private Integer parseIntOrNull(String value) {
        try {
            if (value == null) return null;
            String v = value.trim();
            if (v.isEmpty()) return null;
            return Integer.parseInt(v);
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDate safeDate(Integer year, Integer month, Integer day) {
        try {
            if (year == null || month == null || day == null) return null;
            return LocalDate.of(year, month, day);
        } catch (Exception e) {
            return null;
        }
    }

    private Integer monthAbbrevToNumber(String mon) {
        if (mon == null) return null;
        String m = mon.trim().toUpperCase(java.util.Locale.ROOT);
        if (m.isEmpty()) return null;
        return switch (m) {
            case "JAN" -> 1;
            case "FEV" -> 2;
            case "MAR" -> 3;
            case "ABR" -> 4;
            case "MAI" -> 5;
            case "JUN" -> 6;
            case "JUL" -> 7;
            case "AGO" -> 8;
            case "SET" -> 9;
            case "OUT" -> 10;
            case "NOV" -> 11;
            case "DEZ" -> 12;
            default -> null;
        };
    }
}
