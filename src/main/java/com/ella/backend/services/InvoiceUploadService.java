package com.ella.backend.services;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.ella.backend.classification.ClassificationService;
import com.ella.backend.dto.FinancialTransactionResponseDTO;
import com.ella.backend.dto.InvoiceUploadResponseDTO;
import com.ella.backend.entities.CreditCard;
import com.ella.backend.entities.FinancialTransaction;
import com.ella.backend.entities.Invoice;
import com.ella.backend.entities.User;
import com.ella.backend.enums.TransactionScope;
import com.ella.backend.enums.TransactionStatus;
import com.ella.backend.enums.TransactionType;
import com.ella.backend.repositories.CreditCardRepository;
import com.ella.backend.repositories.FinancialTransactionRepository;
import com.ella.backend.repositories.InstallmentRepository;
import com.ella.backend.repositories.InvoiceRepository;
import com.ella.backend.services.ocr.OcrProperties;
import com.ella.backend.services.ocr.PdfOcrExtractor;
import com.ella.backend.services.ocr.PdfTextExtractor;
import com.ella.backend.services.invoices.parsers.InvoiceParserFactory;
import com.ella.backend.services.invoices.parsers.InvoiceParserStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceUploadService {

    private static final String BUILD_MARKER = "2025-12-29T1022";

    private final FinancialTransactionRepository transactionRepository;
    private final UserService userService;
    private final ClassificationService classificationService;
    private final CreditCardRepository creditCardRepository;
    private final InvoiceRepository invoiceRepository;
    private final InstallmentRepository installmentRepository;
    private final InvoiceParserFactory invoiceParserFactory;
    private final PdfTextExtractor pdfTextExtractor;
    private final PdfOcrExtractor pdfOcrExtractor;
    private final OcrProperties ocrProperties;
    
    @Transactional
    @CacheEvict(cacheNames = "dashboard", allEntries = true)
    public InvoiceUploadResponseDTO processInvoice(MultipartFile file, String password, String dueDate) {
        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw new IllegalArgumentException("Filename cannot be null");
        }

        boolean isPdf = filename.toLowerCase().endsWith(".pdf");

        try (InputStream is = file.getInputStream()) {
            List<TransactionData> transactions;
            if (isPdf) {
                transactions = parsePdf(is, password, dueDate);
            } else {
                transactions = parseCsv(is);
            }

            if (transactions == null || transactions.isEmpty()) {
                if (isPdf) {
                    throw new IllegalArgumentException(
                        (password != null && !password.isBlank()
                            ? "Não foi possível extrair transações desse PDF mesmo com senha informada. "
                            : "Não foi possível extrair transações desse PDF. ") +
                            "Ele pode estar escaneado (imagem), ter restrição de extração de texto, " +
                            "ou ter um layout ainda não suportado. " +
                            "Tente exportar/enviar um CSV, ou um PDF com texto selecionável."
                    );
                }
                throw new IllegalArgumentException(
                        "Não encontrei transações neste arquivo. Confira se o CSV tem cabeçalho e colunas compatíveis."
                );
            }
            return processTransactions(transactions, filename);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to process file", e);
        }
    }
@Transactional
    @CacheEvict(cacheNames = "dashboard", allEntries = true)
    public InvoiceUploadResponseDTO processInvoice(MultipartFile file, String password) {
        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw new IllegalArgumentException("Filename cannot be null");
        }

        boolean isPdf = filename.toLowerCase().endsWith(".pdf");

        try (InputStream is = file.getInputStream()) {
            List<TransactionData> transactions;
            if (isPdf) {
                transactions = parsePdf(is, password);
            } else {
                transactions = parseCsv(is);
            }

            if (transactions == null || transactions.isEmpty()) {
                if (isPdf) {
                    throw new IllegalArgumentException(
                        (password != null && !password.isBlank()
                            ? "Não foi possível extrair transações desse PDF mesmo com senha informada. "
                            : "Não foi possível extrair transações desse PDF. ") +
                            "Ele pode estar escaneado (imagem), ter restrição de extração de texto, " +
                            "ou ter um layout ainda não suportado. " +
                            "Tente exportar/enviar um CSV, ou um PDF com texto selecionável."
                    );
                }
                throw new IllegalArgumentException(
                        "Não encontrei transações neste arquivo. Confira se o CSV tem cabeçalho e colunas compatíveis."
                );
            }
            return processTransactions(transactions, filename);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to process file", e);
        }
    }

    private List<TransactionData> parseCsv(InputStream inputStream) throws IOException {
        List<TransactionData> transactions = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            boolean isFirstLine = true;
            CsvFormat format = null;

            while ((line = reader.readLine()) != null) {
                if (line.startsWith("\uFEFF")) line = line.substring(1);
                line = line.trim();
                if (line.isEmpty()) continue;

                if (isFirstLine) {
                    isFirstLine = false;
                    format = detectCsvFormat(line);
                    continue;
                }

                if (format == null) throw new IllegalArgumentException("Formato de CSV não reconhecido");

                TransactionData data = parseLine(line, format);
                if (data != null) transactions.add(data);
            }
        }
        return transactions;
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

    private List<TransactionData> parsePdf(InputStream inputStream, String password, String dueDateOverride) throws IOException {
        LocalDate dueDateFromRequest = null;
        if (dueDateOverride != null && !dueDateOverride.isBlank()) {
            dueDateFromRequest = parseDueDateOverrideOrThrow(dueDateOverride);
        }

        try (PDDocument document = (password != null && !password.isBlank())
                ? PDDocument.load(inputStream, password)
                : PDDocument.load(inputStream)) {
            try {
                document.setAllSecurityToBeRemoved(true);
            } catch (Exception ignored) {
            }

            String text = pdfTextExtractor.extractText(document);
            if (text == null) text = "";
            if (text.isBlank()) {
                return List.of();
            }

            log.info("[InvoiceUpload][BUILD_MARKER={}] PDF extracted sample: {}", BUILD_MARKER,
                    (text.length() > 500 ? text.substring(0, 500) : text));

            var parserOpt = invoiceParserFactory.getParser(text);
            if (parserOpt.isEmpty()) {
                throw new IllegalArgumentException("Layout de fatura não suportado.");
            }
            InvoiceParserStrategy parser = parserOpt.get();
            log.info("[InvoiceUpload] Selected parser={} (buildMarker={})", parser.getClass().getSimpleName(), BUILD_MARKER);

            LocalDate invoiceDueDate = parser.extractDueDate(text);
            InvoiceParserStrategy parserToUse = parser;
            String textToUse = text;            String ocrText = null;            boolean usedOcrText = false;
            if (invoiceDueDate == null && ocrProperties.isEnabled()) {
                log.warn("[InvoiceUpload][OCR] Due date not found via PDF text. Retrying once with OCR...");
                ocrText = pdfOcrExtractor.extractText(document);
                if (ocrText != null && !ocrText.isBlank()) {
                    log.info("[InvoiceUpload][OCR] Extracted sample: {}",
                            (ocrText.length() > 500 ? ocrText.substring(0, 500) : ocrText));

                    var parserOptOcr = invoiceParserFactory.getParser(ocrText);
                    InvoiceParserStrategy parserOcr = parserOptOcr.orElse(parser);
                    LocalDate ocrDueDate = parserOcr.extractDueDate(ocrText);
                    if (ocrDueDate != null) {
                        invoiceDueDate = ocrDueDate;
                        parserToUse = parserOcr;
                        textToUse = ocrText;
                        usedOcrText = true;
                        log.info("[InvoiceUpload][OCR] Using parser={} dueDate={}",
                                parserToUse.getClass().getSimpleName(), invoiceDueDate);
                    }
                }
            }

            if (invoiceDueDate == null && dueDateFromRequest != null) {
                log.warn("[InvoiceUpload] Using dueDate override from request: {}", dueDateFromRequest);
                invoiceDueDate = dueDateFromRequest;
            }
            if (invoiceDueDate == null) {
                throw new IllegalArgumentException(
                        "Não foi possível determinar a data de vencimento da fatura. " +
                                "O processamento foi interrompido para evitar lançamentos incorretos."
                );
            }

            List<com.ella.backend.services.invoices.parsers.TransactionData> parsed = parserToUse.extractTransactions(textToUse);
            List<TransactionData> transactions = new ArrayList<>();

            for (com.ella.backend.services.invoices.parsers.TransactionData p : parsed) {
                InstallmentInfo installment = (p.installmentNumber != null && p.installmentTotal != null)
                        ? new InstallmentInfo(p.installmentNumber, p.installmentTotal)
                        : null;

                TransactionData data = new TransactionData(
                        p.description,
                        p.amount,
                        p.type,
                        p.category != null ? p.category : "Outros",
                        p.date,
                        p.cardName,
                        p.scope,
                        installment
                );
                data.dueDate = invoiceDueDate;
                transactions.add(data);
            }

            log.info("[InvoiceUpload] Using parser={} dueDate={} txCount={}",
                    parserToUse.getClass().getSimpleName(), invoiceDueDate, transactions.size());

            if (!usedOcrText && ocrProperties.isEnabled() &&
                    (shouldRetryWithOcrForQuality(transactions) ||
                            shouldRetryDueToMissingTransactions(transactions, textToUse))) {
                log.warn("[InvoiceUpload][OCR] Parsed transactions look low-quality. Retrying once with OCR...");
                if (ocrText == null || ocrText.isBlank()) {
                    ocrText = pdfOcrExtractor.extractText(document);
                }
                if (ocrText != null && !ocrText.isBlank()) {
                    var parserOptOcr = invoiceParserFactory.getParser(ocrText);
                    InvoiceParserStrategy parserOcr = parserOptOcr.orElse(parserToUse);

                    List<com.ella.backend.services.invoices.parsers.TransactionData> parsedOcr = parserOcr.extractTransactions(ocrText);
                    List<TransactionData> ocrTransactions = new ArrayList<>();

                    for (com.ella.backend.services.invoices.parsers.TransactionData p : parsedOcr) {
                        InstallmentInfo installment = (p.installmentNumber != null && p.installmentTotal != null)
                                ? new InstallmentInfo(p.installmentNumber, p.installmentTotal)
                                : null;

                        TransactionData data = new TransactionData(
                                p.description,
                                p.amount,
                                p.type,
                                p.category != null ? p.category : "Outros",
                                p.date,
                                p.cardName,
                                p.scope,
                                installment
                        );
                        data.dueDate = invoiceDueDate;
                        ocrTransactions.add(data);
                    }

                    if (isOcrResultBetter(transactions, ocrTransactions)) {
                        log.info("[InvoiceUpload][OCR] OCR retry accepted: txCount {} -> {}", transactions.size(), ocrTransactions.size());
                        return ocrTransactions;
                    }
                    log.info("[InvoiceUpload][OCR] OCR retry rejected: quality not improved (txCount {} -> {})", transactions.size(), ocrTransactions.size());
                }
            }

            return transactions;
        } catch (org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException e) {
            if (password != null && !password.isBlank()) {
                throw new IllegalArgumentException("Senha incorreta para o arquivo PDF.");
            }
            throw new IllegalArgumentException("O arquivo PDF está protegido por senha. Por favor, forneça a senha.");
        }
    }
    private List<TransactionData> parsePdf(InputStream inputStream, String password) throws IOException {
        try (PDDocument document = (password != null && !password.isBlank())
                ? PDDocument.load(inputStream, password)
                : PDDocument.load(inputStream)) {
            try {
                document.setAllSecurityToBeRemoved(true);
            } catch (Exception ignored) {
            }

            String text = pdfTextExtractor.extractText(document);
            if (text == null) text = "";
            if (text.isBlank()) {
                return List.of();
            }

            log.info("[InvoiceUpload][BUILD_MARKER={}] PDF extracted sample: {}", BUILD_MARKER,
                    (text.length() > 500 ? text.substring(0, 500) : text));

            var parserOpt = invoiceParserFactory.getParser(text);
            if (parserOpt.isEmpty()) {
                throw new IllegalArgumentException("Layout de fatura não suportado.");
            }
            InvoiceParserStrategy parser = parserOpt.get();
            log.info("[InvoiceUpload] Selected parser={} (buildMarker={})", parser.getClass().getSimpleName(), BUILD_MARKER);

            LocalDate invoiceDueDate = parser.extractDueDate(text);
            InvoiceParserStrategy parserToUse = parser;
            String textToUse = text;            String ocrText = null;            boolean usedOcrText = false;
            if (invoiceDueDate == null && ocrProperties.isEnabled()) {
                log.warn("[InvoiceUpload][OCR] Due date not found via PDF text. Retrying once with OCR...");
                ocrText = pdfOcrExtractor.extractText(document);
                if (ocrText != null && !ocrText.isBlank()) {
                    log.info("[InvoiceUpload][OCR] Extracted sample: {}",
                            (ocrText.length() > 500 ? ocrText.substring(0, 500) : ocrText));

                    var parserOptOcr = invoiceParserFactory.getParser(ocrText);
                    InvoiceParserStrategy parserOcr = parserOptOcr.orElse(parser);
                    LocalDate ocrDueDate = parserOcr.extractDueDate(ocrText);
                    if (ocrDueDate != null) {
                        invoiceDueDate = ocrDueDate;
                        parserToUse = parserOcr;
                        textToUse = ocrText;
                        usedOcrText = true;
                        log.info("[InvoiceUpload][OCR] Using parser={} dueDate={}",
                                parserToUse.getClass().getSimpleName(), invoiceDueDate);
                    }
                }
            }

            if (invoiceDueDate == null) {
                throw new IllegalArgumentException(
                        "Não foi possível determinar a data de vencimento da fatura. " +
                                "O processamento foi interrompido para evitar lançamentos incorretos."
                );
            }

            List<com.ella.backend.services.invoices.parsers.TransactionData> parsed = parserToUse.extractTransactions(textToUse);
            List<TransactionData> transactions = new ArrayList<>();

            for (com.ella.backend.services.invoices.parsers.TransactionData p : parsed) {
                InstallmentInfo installment = (p.installmentNumber != null && p.installmentTotal != null)
                        ? new InstallmentInfo(p.installmentNumber, p.installmentTotal)
                        : null;

                TransactionData data = new TransactionData(
                        p.description,
                        p.amount,
                        p.type,
                        p.category != null ? p.category : "Outros",
                        p.date,
                        p.cardName,
                        p.scope,
                        installment
                );
                data.dueDate = invoiceDueDate;
                transactions.add(data);
            }

            log.info("[InvoiceUpload] Using parser={} dueDate={} txCount={}",
                    parserToUse.getClass().getSimpleName(), invoiceDueDate, transactions.size());

            if (!usedOcrText && ocrProperties.isEnabled() &&
                    (shouldRetryWithOcrForQuality(transactions) ||
                            shouldRetryDueToMissingTransactions(transactions, textToUse))) {
                log.warn("[InvoiceUpload][OCR] Parsed transactions look low-quality. Retrying once with OCR...");
                ocrText = pdfOcrExtractor.extractText(document);
                if (ocrText != null && !ocrText.isBlank()) {
                    var parserOptOcr = invoiceParserFactory.getParser(ocrText);
                    InvoiceParserStrategy parserOcr = parserOptOcr.orElse(parserToUse);

                    List<com.ella.backend.services.invoices.parsers.TransactionData> parsedOcr = parserOcr.extractTransactions(ocrText);
                    List<TransactionData> ocrTransactions = new ArrayList<>();

                    for (com.ella.backend.services.invoices.parsers.TransactionData p : parsedOcr) {
                        InstallmentInfo installment = (p.installmentNumber != null && p.installmentTotal != null)
                                ? new InstallmentInfo(p.installmentNumber, p.installmentTotal)
                                : null;

                        TransactionData data = new TransactionData(
                                p.description,
                                p.amount,
                                p.type,
                                p.category != null ? p.category : "Outros",
                                p.date,
                                p.cardName,
                                p.scope,
                                installment
                        );
                        data.dueDate = invoiceDueDate;
                        ocrTransactions.add(data);
                    }

                    if (isOcrResultBetter(transactions, ocrTransactions)) {
                        log.info("[InvoiceUpload][OCR] OCR retry accepted: txCount {} -> {}", transactions.size(), ocrTransactions.size());
                        return ocrTransactions;
                    }
                    log.info("[InvoiceUpload][OCR] OCR retry rejected: quality not improved (txCount {} -> {})", transactions.size(), ocrTransactions.size());
                }
            }

            return transactions;
        } catch (org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException e) {
            if (password != null && !password.isBlank()) {
                throw new IllegalArgumentException("Senha incorreta para o arquivo PDF.");
            }
            throw new IllegalArgumentException("O arquivo PDF está protegido por senha. Por favor, forneça a senha.");
        }
    }

    static boolean shouldRetryWithOcrForQuality(List<TransactionData> transactions) {
        if (transactions == null || transactions.isEmpty()) return false;

        int total = transactions.size();
        int missingDate = 0;
        int garbled = 0;

        for (TransactionData tx : transactions) {
            if (tx == null) continue;
            if (tx.date == null) missingDate++;
            if (isLikelyGarbledMerchant(tx.description)) garbled++;
        }

        boolean manyMissingDates = missingDate >= Math.max(2, (int) Math.ceil(total * 0.5));
        boolean manyGarbledDescs = garbled >= Math.max(2, (int) Math.ceil(total * 0.4));
        boolean trigger = manyMissingDates || manyGarbledDescs;

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
            log.info("[InvoiceUpload][OCR] Quality trigger: total={} garbled={} missingDate={} samples={}", total, garbled, missingDate, sample.toString().trim());
        }

        return trigger;
    }

    static boolean isOcrResultBetter(List<TransactionData> original, List<TransactionData> ocr) {
        return qualityScore(ocr) > qualityScore(original);
    }

    static int qualityScore(List<TransactionData> transactions) {
        if (transactions == null || transactions.isEmpty()) return 0;

        int score = 0;
        for (TransactionData tx : transactions) {
            if (tx == null) continue;
            if (tx.date != null) score += 2;
            if (tx.description != null && !tx.description.isBlank()) score += 1;
            if (isLikelyGarbledMerchant(tx.description)) score -= 2;
        }
        return score;
    }

    static boolean isLikelyGarbledMerchant(String description) {
        if (description == null) return false;
        String d = description.trim();
        if (d.isEmpty()) return false;

        String upper = d.toUpperCase();

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
    private boolean shouldRetryDueToMissingTransactions(List<TransactionData> transactions, String text) {
        // Extrair total esperado
        Pattern pattern = Pattern.compile("(?i)total\\s+(?:da\\s+)?fatura[:\\s]+R?\\$?\\s*([\\d.,]+)");
        Matcher m = pattern.matcher(text);
        if (!m.find()) return false;

        BigDecimal expectedTotal = parseBrlAmount(m.group(1));
        if (expectedTotal == null) return false;

        // Calcular total extraído
        BigDecimal totalExtracted = transactions.stream()
                .map(t -> t.amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Se extraído < 95% do esperado, tentar OCR
        BigDecimal threshold = expectedTotal.multiply(BigDecimal.valueOf(0.95));
        return totalExtracted.compareTo(threshold) < 0;
    }

    // ✨ NOVO: Converter valor BRL em BigDecimal
    private BigDecimal parseBrlAmount(String raw) {
        if (raw == null) return null;
        String s = raw.trim().replace("R$", "").replace(" ", "").trim();
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


    private String normalizeSectionLine(String line) {
        if (line == null) return "";
        String normalized = Normalizer.normalize(line, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .trim();
        // normaliza múltiplos espaços
        return normalized.replaceAll("\\s+", " ");
    }

    private LocalDate extractInvoiceDueDateFromPdfText(String text) {
        if (text == null || text.isBlank()) return null;

        // Tenta capturar "Vencimento: dd/MM" ou "Vencimento: dd/MM/yyyy" (variações comuns em faturas PT-BR)
        // Nota: depende do PDF ter texto selecionável (não só imagem).
        List<Pattern> patterns = List.of(
            Pattern.compile("(?i)\\bvenc(?:imento)?\\b\\s*[:\\-]?\\s*(\\d{2}/\\d{2}(?:/\\d{2,4})?)"),
            Pattern.compile("(?i)\\bvcto\\b\\s*[:\\-]?\\s*(\\d{2}/\\d{2}(?:/\\d{2,4})?)"),
            Pattern.compile("(?i)\\bdata\\s+de\\s+venc(?:imento)?\\b\\s*[:\\-]?\\s*(\\d{2}/\\d{2}(?:/\\d{2,4})?)"),
            Pattern.compile("(?i)\\bvenc\\.?\\s*[:\\-]?\\s*(\\d{2}/\\d{2}(?:/\\d{2,4})?)")
        );

        for (Pattern p : patterns) {
            Matcher m = p.matcher(text);
            if (m.find()) {
                String dateStr = m.group(1);
                try {
                    if (dateStr.matches("\\d{2}/\\d{2}/\\d{4}")) {
                        return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                    }
                    if (dateStr.matches("\\d{2}/\\d{2}/\\d{2}")) {
                        // Interpreta yy como 20yy
                        return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("dd/MM/yy"));
                    }
                    // dd/MM (usa heurística do parseDate existente)
                    return parseDate(dateStr);
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    private TransactionData parsePdfLine(String line) {
        try {
            line = line.trim();
            if (line.isEmpty()) return null;

            String normalizedLine = normalizeSectionLine(line);
            // Evita falsos positivos em linhas de cabeçalho/total
            if (normalizedLine.contains("total")
                    || normalizedLine.contains("venc")
                    || normalizedLine.contains("pagamento minimo")
                    || normalizedLine.contains("limite")
                    || normalizedLine.contains("resumo")
                    || normalizedLine.contains("saldo")) {
                // ainda assim, algumas faturas podem ter compras com a palavra "total" na descrição; então só filtra
                // quando não parece ter um valor monetário no final.
                if (!normalizedLine.matches(".*(r\\$\\s*)?\\(?-?\\d{1,3}(?:\\.\\d{3})*,\\d{2}\\)?\\s*$")) {
                    return null;
                }
            }

            // Regex tolerante para capturar: data, descrição, valor.
            // Suporta: dd/MM, dd/MM/yy, dd/MM/yyyy, dd MMM; valor pode vir com R$, milhares e parênteses.
            Pattern pattern = Pattern.compile(
                    "^(\\d{2}\\s+[A-Za-z]{3}|\\d{2}/\\d{2}(?:/\\d{2,4})?)\\s+(.+?)\\s+(?:R\\$\\s*)?(\\(?-?[\\d\\.,]+\\)?)\\s*$",
                    Pattern.CASE_INSENSITIVE
            );
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                String dateStr = matcher.group(1);
                String desc = matcher.group(2);
                String amountStr = matcher.group(3);
                
                log.debug("[InvoiceUpload] Matched line desc='{}' amount='{}'", desc, amountStr);

                LocalDate date = parseDate(dateStr);
                
                // Limpar valor
                boolean negative = false;
                String raw = amountStr.trim();
                if (raw.startsWith("(") && raw.endsWith(")")) {
                    negative = true;
                    raw = raw.substring(1, raw.length() - 1).trim();
                }
                if (raw.startsWith("-")) {
                    negative = true;
                }

                raw = raw.replace("R$", "").trim();
                raw = raw.replace(".", "").replace(",", ".");
                raw = raw.replace("-", "");
                BigDecimal amount = new BigDecimal(raw);
                if (negative) {
                    amount = amount.negate();
                }
                
                // Lógica invertida para faturas de cartão:
                // Valores positivos são DESPESAS (compras)
                // Valores negativos são CRÉDITOS (pagamentos/estornos)
                TransactionType type;
                if (amount.compareTo(BigDecimal.ZERO) < 0) {
                    type = TransactionType.INCOME; // Crédito na fatura
                } else {
                    // Verifica se é um pagamento explícito
                    if (desc.toLowerCase().contains("pagamento") || desc.toLowerCase().contains("payment")) {
                        type = TransactionType.INCOME;
                    } else {
                        type = TransactionType.EXPENSE;
                    }
                }
                
                String category = categorizeDescription(desc, type);
                TransactionScope scope = inferScope(desc, null);
                InstallmentInfo installment = extractInstallmentInfo(desc);
                
                return new TransactionData(desc, amount.abs(), type, category, date, null, scope, installment);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private InvoiceUploadResponseDTO processTransactions(List<TransactionData> transactions, String originalFilename) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) throw new IllegalStateException("Usuário não autenticado");
        User user = userService.findByEmail(auth.getName());

        List<FinancialTransactionResponseDTO> responseTransactions = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        Invoice lastInvoice = null;
        
        // Cache para evitar múltiplas consultas/criações do mesmo cartão no mesmo upload
        Map<String, CreditCard> cardCache = new HashMap<>();

        for (TransactionData data : transactions) {
             CardMetadata cardMetadata = extractCardMetadata(data.cardName, originalFilename);
             String cacheKey = (cardMetadata.brand() + "|" + (cardMetadata.lastFourDigits() != null ? cardMetadata.lastFourDigits() : cardMetadata.name())).toLowerCase();
             
             CreditCard card = cardCache.computeIfAbsent(cacheKey, key -> resolveOrCreateCard(user, cardMetadata));
             
             if (card != null) {
                 LocalDate invoiceDueDate = data.dueDate;
                 if (invoiceDueDate == null) {
                     // Fallback: se não conseguimos extrair vencimento do arquivo, usamos a lógica antiga (baseada na data da compra)
                     invoiceDueDate = estimateDueDateFromCardAndTxDate(card, data.date);
                 }

                 Invoice invoice = getOrCreateInvoice(card, invoiceDueDate);
                 lastInvoice = invoice;
                 
                 FinancialTransaction tx = saveTransaction(user, data, invoiceDueDate);
                 createOrLinkInstallment(invoice, tx, data);
                 
                 if (data.type == TransactionType.EXPENSE) {
                     invoice.setTotalAmount(invoice.getTotalAmount().add(data.amount));
                 } else {
                     invoice.setTotalAmount(invoice.getTotalAmount().subtract(data.amount));
                 }
                 invoiceRepository.save(invoice);
                 
                 responseTransactions.add(mapToDTO(tx));
                 totalAmount = totalAmount.add(tx.getAmount());
             }
        }
        
        LocalDate startDate = transactions.stream().map(t -> t.date).min(LocalDate::compareTo).orElse(null);
        LocalDate endDate = transactions.stream().map(t -> t.date).max(LocalDate::compareTo).orElse(null);

        return InvoiceUploadResponseDTO.builder()
                .invoiceId(lastInvoice != null ? lastInvoice.getId() : null)
                .totalAmount(totalAmount)
                .totalTransactions(responseTransactions.size())
                .startDate(startDate)
                .endDate(endDate)
                .transactions(responseTransactions)
                .build();
    }

    private FinancialTransaction saveTransaction(User user, TransactionData txData, LocalDate invoiceDueDate) {
        LocalDate resolvedDueDate = txData.dueDate != null ? txData.dueDate : invoiceDueDate;
        if (resolvedDueDate == null) {
            resolvedDueDate = txData.date;
        }

        String resolvedCategory = txData.category;
        if (isUncategorizedCategory(resolvedCategory)) {
            var suggestion = classificationService.suggest(user.getId(), txData.description, txData.amount, txData.type);
            resolvedCategory = suggestion.category();
        }

        FinancialTransaction entity = FinancialTransaction.builder()
                .person(user)
                .description(txData.description)
                .amount(txData.amount)
                .type(txData.type)
                .scope(txData.scope != null ? txData.scope : TransactionScope.PERSONAL)
                .category(resolvedCategory)
                // Regra: referência (mês do dashboard) = vencimento da fatura
                .transactionDate(resolvedDueDate)
                // Preserva a data original da compra/linha
                .purchaseDate(txData.date)
                .dueDate(resolvedDueDate)
                .status(TransactionStatus.PENDING)
                .build();
        return Objects.requireNonNull(transactionRepository.save(entity));
    }

    private boolean isUncategorizedCategory(String category) {
        if (category == null) return true;
        String c = category.trim();
        if (c.isEmpty()) return true;
        return "Outros".equalsIgnoreCase(c) || "Other".equalsIgnoreCase(c);
    }

    private LocalDate estimateDueDateFromCardAndTxDate(CreditCard card, LocalDate txDate) {
        if (txDate == null) return null;
        Integer dueDay = null;
        try {
            dueDay = card != null ? card.getDueDay() : null;
        } catch (Exception ignored) {}

        int safeDueDay = (dueDay != null && dueDay >= 1 && dueDay <= 28) ? dueDay : 10;
        LocalDate base = LocalDate.of(txDate.getYear(), txDate.getMonthValue(), 1);
        LocalDate nextMonth = base.plusMonths(1);
        int dom = Math.min(safeDueDay, nextMonth.lengthOfMonth());
        return nextMonth.withDayOfMonth(dom);
    }

    private FinancialTransactionResponseDTO mapToDTO(FinancialTransaction tx) {
        return new FinancialTransactionResponseDTO(
            tx.getId().toString(),
            tx.getPerson().getId().toString(),
            tx.getPerson().getName(),
            tx.getDescription(),
            tx.getAmount(),
            tx.getType(),
            tx.getScope(),
            tx.getCategory(),
            tx.getTripId() != null ? tx.getTripId().toString() : null,
            tx.getTripSubcategory(),
            tx.getTransactionDate(),
            tx.getPurchaseDate(),
            tx.getDueDate(),
            tx.getPaidDate(),
            tx.getStatus(),
            tx.getCreatedAt(),
            tx.getUpdatedAt()
        );
    }

    private CreditCard resolveOrCreateCard(User user, CardMetadata cardMetadata) {
        try {
            var cards = creditCardRepository.findByOwner(user);
            if (cards != null) {
                // Prioriza match por últimos 4 dígitos + bandeira
                if (cardMetadata.lastFourDigits() != null) {
                    for (var c : cards) {
                        if (c.getLastFourDigits() != null
                                && c.getLastFourDigits().equals(cardMetadata.lastFourDigits())
                                && equalsIgnoreCase(c.getBrand(), cardMetadata.brand())) {
                            return c;
                        }
                    }
                }

                // Fallback: nome + bandeira
                for (var c : cards) {
                    if (equalsIgnoreCase(c.getName(), cardMetadata.name())
                            && equalsIgnoreCase(c.getBrand(), cardMetadata.brand())) {
                        return c;
                    }
                }
            }
        } catch (Exception ignored) {}

        CreditCard cc = new CreditCard();
        cc.setOwner(user);
        cc.setName(cardMetadata.name());
        cc.setBrand(cardMetadata.brand());
        cc.setLastFourDigits(cardMetadata.lastFourDigits());
        cc.setLimitAmount(java.math.BigDecimal.valueOf(10000));
        cc.setClosingDay(5);
        cc.setDueDay(15);
        return creditCardRepository.save(cc);
    }

    private CardMetadata extractCardMetadata(String candidateName, String filename) {
        String metaSource = candidateName != null ? candidateName : "";
        String brand = detectBrand(metaSource);
        if (brand == null) {
            brand = detectBrand(filename);
        }

        String lastFour = detectLastFour(metaSource);
        if (lastFour == null) {
            lastFour = detectLastFour(filename);
        }

        String name = (candidateName != null && !candidateName.isBlank()) ? candidateName.trim() : null;
        if (name == null) {
            name = brand != null ? brand : (filename != null && !filename.isBlank() ? filename : "Cartão Padrão");
        }
        if (brand == null) {
            brand = name;
        }

        return new CardMetadata(name, brand, lastFour);
    }

    private String detectBrand(String source) {
        if (source == null) return null;
        String s = source.toLowerCase();
        if (s.contains("visa")) return "Visa";
        if (s.contains("mastercard") || s.contains("master")) return "Mastercard";
        if (s.contains("elo")) return "Elo";
        if (s.contains("amex") || s.contains("american express")) return "Amex";
        if (s.contains("hipercard")) return "Hipercard";
        if (s.contains("diners")) return "Diners";
        if (s.contains("nubank")) return "Nubank";
        if (s.contains("santander")) return "Santander";
        if (s.contains("itau")) return "Itaú";
        if (s.contains("bradesco")) return "Bradesco";
        if (s.contains("c6")) return "C6 Bank";
        if (s.contains("inter")) return "Banco Inter";
        return null;
    }

    private String detectLastFour(String source) {
        if (source == null) return null;
        Pattern lastFourPattern = Pattern.compile("(?:\\u002A{4}|\\*)?\\s*(\\d{4})(?!.*\\d)");
        Matcher m = lastFourPattern.matcher(source);
        if (m.find()) {
            return m.group(1);
        }

        Pattern finalPattern = Pattern.compile("final\\s*(\\d{4})", Pattern.CASE_INSENSITIVE);
        Matcher m2 = finalPattern.matcher(source);
        if (m2.find()) {
            return m2.group(1);
        }
        return null;
    }

    private Invoice getOrCreateInvoice(CreditCard card, LocalDate invoiceDueDate) {
        final LocalDate resolvedInvoiceDueDate = (invoiceDueDate != null) ? invoiceDueDate : LocalDate.now();

        int month = resolvedInvoiceDueDate.getMonthValue();
        int year = resolvedInvoiceDueDate.getYear();
        return invoiceRepository
                .findByCardAndMonthAndYear(card, month, year)
                .orElseGet(() -> {
                    Invoice inv = new Invoice();
                    inv.setCard(card);
                    inv.setMonth(month);
                    inv.setYear(year);
                    inv.setDueDate(resolvedInvoiceDueDate);
                    inv.setTotalAmount(BigDecimal.ZERO);
                    inv.setPaidAmount(BigDecimal.ZERO);
                    return invoiceRepository.save(inv);
                });
    }

    private void createOrLinkInstallment(Invoice invoice, FinancialTransaction entity, TransactionData txData) {
        int installmentNumber = txData.installmentNumber != null ? txData.installmentNumber : 1;
        int installmentTotal = txData.installmentTotal != null ? txData.installmentTotal : 1;
        LocalDate installmentDueDate = txData.dueDate != null ? txData.dueDate : invoice.getDueDate();
        if (installmentDueDate == null) {
            installmentDueDate = txData.date;
        }

        try {
            var existing = installmentRepository.findByTransaction(entity);
            if (existing != null && !existing.isEmpty()) {
                var inst = existing.get(0);
                boolean changed = false;
                if (!invoice.equals(inst.getInvoice())) {
                    inst.setInvoice(invoice);
                    changed = true;
                }
                if (inst.getNumber() == null || !inst.getNumber().equals(installmentNumber)) {
                    inst.setNumber(installmentNumber);
                    changed = true;
                }
                if (inst.getTotal() == null || !inst.getTotal().equals(installmentTotal)) {
                    inst.setTotal(installmentTotal);
                    changed = true;
                }
                if (inst.getAmount() == null || inst.getAmount().compareTo(txData.amount) != 0) {
                    inst.setAmount(txData.amount);
                    changed = true;
                }
                if (inst.getDueDate() == null || !inst.getDueDate().equals(installmentDueDate)) {
                    inst.setDueDate(installmentDueDate);
                    changed = true;
                }

                if (changed) {
                    installmentRepository.save(inst);
                }
                return;
            }
        } catch (Exception ignored) {}

        var installment = new com.ella.backend.entities.Installment();
        installment.setInvoice(invoice);
        installment.setTransaction(entity);
        installment.setNumber(installmentNumber);
        installment.setTotal(installmentTotal);
        installment.setAmount(txData.amount);
        installment.setDueDate(installmentDueDate);
        installmentRepository.save(installment);
    }

    private CsvFormat detectCsvFormat(String header) {
        String h = header.toLowerCase();
        if (h.contains("nome") && h.contains("cpf") && h.contains("cartão") && h.contains("data")) {
            return CsvFormat.FULL_EXPORT;
        }
        if (h.contains("data") && h.contains("descrição") && h.contains("valor")) {
            return CsvFormat.PORTUGUESE;
        }
        if (h.contains("description") && h.contains("amount") && h.contains("date")) {
            return CsvFormat.ENGLISH;
        }
        return CsvFormat.UNKNOWN;
    }

    private TransactionData parseLine(String line, CsvFormat format) {
        try {
            String[] fields = line.split(",");

            if (format == CsvFormat.FULL_EXPORT) {
                if (fields.length < 6) return null;
                String cardName = fields[2].trim();
                LocalDate date = parseDate(fields[3].trim());
                String description = fields[4].trim();
                BigDecimal amount = new BigDecimal(fields[5].trim());
                String category = (fields.length > 6) ? fields[6].trim() : "Outros";
                // Inverted logic: Positive amount is EXPENSE (Purchase), Negative is INCOME (Payment/Credit)
                TransactionType type = amount.compareTo(BigDecimal.ZERO) > 0 ? TransactionType.EXPENSE : TransactionType.INCOME;
                category = normalizeCategory(category);
                TransactionScope scope = inferScope(description, cardName);
                InstallmentInfo installment = extractInstallmentInfo(description);
                return new TransactionData(description, amount.abs(), type, category, date, cardName, scope, installment);

            } else if (format == CsvFormat.PORTUGUESE) {
                if (fields.length < 4) return null;
                LocalDate date = parseDate(fields[0].trim());
                String description = fields[1].trim();
                BigDecimal amount = new BigDecimal(fields[2].trim());
                String category = fields[3].trim();
                // Inverted logic: Positive amount is EXPENSE (Purchase), Negative is INCOME (Payment/Credit)
                TransactionType type = amount.compareTo(BigDecimal.ZERO) > 0 ? TransactionType.EXPENSE : TransactionType.INCOME;
                category = normalizeCategory(category);
                TransactionScope scope = inferScope(description, null);
                InstallmentInfo installment = extractInstallmentInfo(description);
                return new TransactionData(description, amount.abs(), type, category, date, null, scope, installment);

            } else if (format == CsvFormat.ENGLISH) {
                if (fields.length < 4) return null;
                String description = fields[0].trim();
                BigDecimal amount = new BigDecimal(fields[1].trim()).abs();
                String category = fields[2].trim();
                LocalDate date = parseDate(fields[3].trim());
                TransactionType type = TransactionType.EXPENSE;
                if (fields.length > 4) {
                    String typeStr = fields[4].trim().toUpperCase();
                    if ("INCOME".equals(typeStr)) {
                        type = TransactionType.INCOME;
                    }
                }
                if (category == null || category.isEmpty()) {
                    category = categorizeDescription(description, type);
                }
                TransactionScope scope = inferScope(description, null);
                InstallmentInfo installment = extractInstallmentInfo(description);
                return new TransactionData(description, amount, type, category, date, null, scope, installment);
            }
            return null;
        } catch (Exception e) {
            System.err.println("[CSV] Erro ao parsear linha: " + line + " - " + e.getMessage());
            return null;
        }
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

    private LocalDate parseDate(String dateStr) {
        try {
            // Tenta formato dd MMM (ex: 12 DEZ)
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
                
                int year = LocalDate.now().getYear();
                LocalDate parsed = LocalDate.of(year, month, day);
                
                // Se a data for futura em relação a hoje + 1 mês, assume ano anterior
                if (parsed.isAfter(LocalDate.now().plusMonths(1))) {
                    return parsed.minusYears(1);
                }
                return parsed;
            }

            return LocalDate.parse(dateStr);
        } catch (Exception e1) {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
                return LocalDate.parse(dateStr, formatter);
            } catch (Exception e2) {
                try {
                    // Tenta formato dd/MM/yy
                    DateTimeFormatter shortYear = DateTimeFormatter.ofPattern("dd/MM/yy");
                    LocalDate parsed = LocalDate.parse(dateStr, shortYear);
                    if (parsed.isAfter(LocalDate.now().plusMonths(1))) {
                        return parsed.minusYears(1);
                    }
                    return parsed;
                } catch (Exception ignored) {
                    // segue para dd/MM
                }

                try {
                    // Tenta formato curto dd/MM assumindo ano atual
                    // Se a data for futura em relação a hoje, assume ano anterior (ex: upload em Jan/25 de fatura de Dez/24)
                    LocalDate parsed = LocalDate.parse(dateStr, new java.time.format.DateTimeFormatterBuilder()
                        .appendPattern("dd/MM")
                        .parseDefaulting(java.time.temporal.ChronoField.YEAR, LocalDate.now().getYear())
                        .toFormatter());
                    
                    if (parsed.isAfter(LocalDate.now().plusMonths(1))) {
                        return parsed.minusYears(1);
                    }
                    return parsed;
                } catch (Exception e3) {
                    return LocalDate.now();
                }
            }
        }
    }

    private String normalizeCategory(String category) {
        if (category == null) return "Outros";
        String c = category.trim().toLowerCase();

        // Normaliza entradas comuns (pt/en) para os rótulos usados no app
        if (c.contains("transporte") || c.contains("transport")) return "Transporte";
        if (c.contains("alimentação") || c.contains("alimentacao") || c.contains("food")) return "Alimentação";
        if (c.contains("mercado") || c.contains("supermerc") || c.contains("grocer")) return "Mercado";
        if (c.contains("stream") || c.contains("streaming")) return "Streaming";
        if (c.contains("lazer") || c.contains("entretenimento") || c.contains("entertain")) return "Lazer";
        if (c.contains("saúde") || c.contains("saude") || c.contains("health") || c.contains("medic")) return "Saúde";
        if (c.contains("farm") || c.contains("drog") || c.contains("farmácia") || c.contains("farmacia")) return "Farmácia";
        if (c.contains("internet")) return "Internet";
        if (c.contains("celular") || c.contains("telefone")) return "Celular";
        if (c.contains("aluguel") || c.contains("rent")) return "Aluguel";
        if (c.contains("moradia") || c.contains("housing") || c.contains("casa")) return "Moradia";

        // Fallback
        if ("other".equals(c) || "outros".equals(c)) return "Outros";
        return category;
    }

    private String categorizeDescription(String description, TransactionType type) {
        String d = description == null ? "" : description.toLowerCase();
        if (type == TransactionType.INCOME) {
            // Para fatura de cartão, créditos podem ser pagamento/estorno; manter genérico.
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

    private boolean equalsIgnoreCase(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equalsIgnoreCase(b);
    }

    private enum CsvFormat {
        PORTUGUESE, ENGLISH, FULL_EXPORT, UNKNOWN
    }

    private static class TransactionData {
        String description;
        BigDecimal amount;
        TransactionType type;
        TransactionScope scope;
        String category;
        LocalDate date;
        LocalDate dueDate;
        String cardName;
        Integer installmentNumber;
        Integer installmentTotal;

        TransactionData(String description, BigDecimal amount, TransactionType type, String category, LocalDate date, String cardName, TransactionScope scope, InstallmentInfo installmentInfo) {
            this.description = description;
            this.amount = amount;
            this.type = type;
            this.scope = scope;
            this.category = category;
            this.date = date;
            this.dueDate = null;
            this.cardName = cardName;
            if (installmentInfo != null) {
                this.installmentNumber = installmentInfo.number();
                this.installmentTotal = installmentInfo.total();
            }
        }
    }

    private record InstallmentInfo(Integer number, Integer total) {}

    private record CardMetadata(String name, String brand, String lastFourDigits) {}
}






