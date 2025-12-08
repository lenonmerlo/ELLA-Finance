package com.ella.backend.services;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.ella.backend.entities.CreditCard;
import com.ella.backend.entities.FinancialTransaction;
import com.ella.backend.entities.Invoice;
import com.ella.backend.entities.User;
import com.ella.backend.enums.TransactionStatus;
import com.ella.backend.enums.TransactionType;
import com.ella.backend.repositories.CreditCardRepository;
import com.ella.backend.repositories.FinancialTransactionRepository;
import com.ella.backend.repositories.InstallmentRepository;
import com.ella.backend.repositories.InvoiceRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class InvoiceUploadService {

    private final FinancialTransactionRepository transactionRepository;
    private final UserService userService;
    private final CreditCardRepository creditCardRepository;
    private final InvoiceRepository invoiceRepository;
    private final InstallmentRepository installmentRepository;

    /**
     * ✅ CORRIGIDO: Aceita múltiplos formatos de CSV
     *
     * Formato 1 (inglês): description,amount,category,date,type
     * Formato 2 (português): Data,Descrição,Valor,Categoria
     *
     * Detecta automaticamente o formato pelo header.
     */
    @SuppressWarnings("null")
    @CacheEvict(cacheNames = "dashboard", allEntries = true)
    public Map<String, Object> parseCsv(InputStream inputStream, String originalFilename) throws Exception {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new IllegalStateException("Usuário não autenticado");
        }
        String email = auth.getName();
        User user = userService.findByEmail(email);

        List<Map<String, Object>> transactionsPayload = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            boolean isFirstLine = true;
            CsvFormat format = null;

            while ((line = reader.readLine()) != null) {
                // Remove BOM se existir
                if (line.startsWith("\uFEFF")) {
                    line = line.substring(1);
                }

                line = line.trim();
                if (line.isEmpty()) continue;

                // Detectar formato pelo header
                if (isFirstLine) {
                    isFirstLine = false;
                    format = detectCsvFormat(line);
                    System.out.println("[CSV] Formato detectado: " + format);
                    continue;
                }

                if (format == null) {
                    throw new IllegalArgumentException("Formato de CSV não reconhecido");
                }

                // Parse linha baseado no formato
                TransactionData txData = parseLine(line, format);
                if (txData == null) continue;

                // Persistir transação
                FinancialTransaction entity = FinancialTransaction.builder()
                        .person(user)
                        .description(txData.description)
                        .amount(txData.amount)
                        .type(txData.type)
                        .category(txData.category)
                        .transactionDate(txData.date)
                        .status(TransactionStatus.PENDING)
                        .build();
                entity = transactionRepository.save(entity);

                System.out.println("[CSV] Transação salva: " + entity.getDescription() + " = " + entity.getAmount());

                // Vincular transação a uma fatura (invoice) via installment
                // Estratégia: identificar/criar cartão automaticamente com base no nome do arquivo ou coluna do CSV
                String cardIdentifier = (txData.cardName != null && !txData.cardName.isEmpty()) 
                                        ? txData.cardName 
                                        : originalFilename;
                CreditCard card = resolveOrCreateCardFromFilename(user, cardIdentifier);
                if (card != null) {
                    Invoice invoice = getOrCreateInvoice(card, txData.date);
                    createOrLinkInstallment(invoice, entity, txData);
                    // Atualiza totais da fatura
                    // Se for despesa, soma. Se for pagamento/crédito, subtrai.
                    if (txData.type == TransactionType.EXPENSE) {
                        invoice.setTotalAmount(invoice.getTotalAmount().add(txData.amount));
                    } else {
                        invoice.setTotalAmount(invoice.getTotalAmount().subtract(txData.amount));
                    }
                    invoiceRepository.save(invoice);
                }

                Map<String, Object> tx = new HashMap<>();
                tx.put("id", entity.getId().toString());
                tx.put("description", entity.getDescription());
                tx.put("amount", entity.getAmount());
                tx.put("category", entity.getCategory());
                tx.put("date", entity.getTransactionDate().toString());
                tx.put("type", entity.getType() == TransactionType.INCOME ? "INCOME" : "EXPENSE");
                transactionsPayload.add(tx);
            }
        }

        System.out.println("[CSV] Total de transações processadas: " + transactionsPayload.size());

        BigDecimal totalIncome = transactionsPayload.stream()
                .filter(t -> "INCOME".equals(t.get("type")))
                .map(t -> (BigDecimal) t.get("amount"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalExpense = transactionsPayload.stream()
                .filter(t -> "EXPENSE".equals(t.get("type")))
                .map(t -> (BigDecimal) t.get("amount"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal balance = totalIncome.subtract(totalExpense);
        int savingsRate = totalIncome.compareTo(BigDecimal.ZERO) > 0
                ? balance.multiply(BigDecimal.valueOf(100)).divide(totalIncome, 0, java.math.RoundingMode.HALF_UP).intValue()
                : 0;

        Map<String, Object> summary = new HashMap<>();
        summary.put("balance", balance);
        summary.put("totalIncome", totalIncome);
        summary.put("totalExpenses", totalExpense);
        summary.put("savingsRate", savingsRate);

        List<Map<String, Object>> insights = new ArrayList<>();
        if (savingsRate >= 30) {
            Map<String, Object> tip = new HashMap<>();
            tip.put("id", 1);
            tip.put("title", "Ótima taxa de poupança!");
            tip.put("description", "Você está economizando " + savingsRate + "% da sua renda mensal.");
            tip.put("type", "TIP");
            tip.put("priority", "HIGH");
            insights.add(tip);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("summary", summary);
        payload.put("transactions", transactionsPayload);
        payload.put("insights", insights);
        return payload;
    }

    private CreditCard resolveOrCreateCardFromFilename(User user, String filename) {
        String cardName = inferCardName(filename);
        // tenta encontrar cartão pelo nome para esse owner
        try {
            var cards = creditCardRepository.findByOwner(user);
            if (cards != null) {
                for (var c : cards) {
                    if (c.getName() != null && c.getName().equalsIgnoreCase(cardName)) {
                        return c;
                    }
                }
            }
        } catch (Exception ignored) {}
        // não achou: cria novo cartão básico
        CreditCard cc = new CreditCard();
        cc.setOwner(user);
        cc.setName(cardName);
        cc.setBrand(cardName); // simples: usar o mesmo nome como brand
        cc.setLastFourDigits("0000");
        cc.setLimitAmount(java.math.BigDecimal.valueOf(10000));
        // Definições padrão para evitar violação de NOT NULL
        cc.setClosingDay(5); // fechamento todo dia 5
        cc.setDueDay(15);    // vencimento todo dia 15
        return creditCardRepository.save(cc);
    }

    private String inferCardName(String filename) {
        if (filename == null || filename.isBlank()) return "Cartão Padrão";
        String f = filename.toLowerCase();
        if (f.contains("nubank")) return "Nubank";
        if (f.contains("visa")) return "Visa";
        if (f.contains("mastercard") || f.contains("master")) return "Mastercard";
        if (f.contains("itau")) return "Itaú";
        if (f.contains("bradesco")) return "Bradesco";
        if (f.contains("santander")) return "Santander";
        if (f.contains("c6")) return "C6 Bank";
        if (f.contains("inter")) return "Banco Inter";
        return filename;
    }

    private Invoice getOrCreateInvoice(CreditCard card, LocalDate txDate) {
        int month = txDate.getMonthValue();
        int year = txDate.getYear();
        return invoiceRepository
                .findByCardAndMonthAndYear(card, month, year)
                .orElseGet(() -> {
                    Invoice inv = new Invoice();
                    inv.setCard(card);
                    inv.setMonth(month);
                    inv.setYear(year);
                    // Due date simples: dia 10 do mês seguinte
                    LocalDate due = LocalDate.of(year, month, 1).plusMonths(1).withDayOfMonth(Math.min(10, LocalDate.of(year, month, 1).plusMonths(1).lengthOfMonth()));
                    inv.setDueDate(due);
                    inv.setTotalAmount(BigDecimal.ZERO);
                    inv.setPaidAmount(BigDecimal.ZERO);
                    return invoiceRepository.save(inv);
                });
    }

    private void createOrLinkInstallment(Invoice invoice, FinancialTransaction entity, TransactionData txData) {
        try {
            var existing = installmentRepository.findByTransaction(entity);
            if (existing != null && !existing.isEmpty()) {
                // Já existe vínculo; se não estiver associado à fatura correta, atualiza
                var inst = existing.get(0);
                if (!invoice.equals(inst.getInvoice())) {
                    inst.setInvoice(invoice);
                    installmentRepository.save(inst);
                }
                return;
            }
        } catch (Exception ignored) {}

        var installment = new com.ella.backend.entities.Installment();
        installment.setInvoice(invoice);
        installment.setTransaction(entity);
        installment.setNumber(1);
        installment.setTotal(1);
        installment.setAmount(txData.amount);
        installment.setDueDate(txData.date);
        installmentRepository.save(installment);
    }

    /**
     * Detecta o formato do CSV pelo header
     */
    private CsvFormat detectCsvFormat(String header) {
        String h = header.toLowerCase();

        // Formato completo: Nome,CPF Final,Cartão,Data,Descrição,Valor,Categoria
        if (h.contains("nome") && h.contains("cpf") && h.contains("cartão") && h.contains("data")) {
            return CsvFormat.FULL_EXPORT;
        }

        // Formato português: Data,Descrição,Valor,Categoria
        if (h.contains("data") && h.contains("descrição") && h.contains("valor")) {
            return CsvFormat.PORTUGUESE;
        }

        // Formato inglês: description,amount,category,date,type
        if (h.contains("description") && h.contains("amount") && h.contains("date")) {
            return CsvFormat.ENGLISH;
        }

        return CsvFormat.UNKNOWN;
    }

    /**
     * Parse uma linha do CSV baseado no formato
     */
    private TransactionData parseLine(String line, CsvFormat format) {
        try {
            String[] fields = line.split(",");

            if (format == CsvFormat.FULL_EXPORT) {
                // Formato: Nome,CPF Final,Cartão,Data,Descrição,Valor,Categoria
                if (fields.length < 6) return null; // Pelo menos até o valor

                String cardName = fields[2].trim();
                LocalDate date = parseDate(fields[3].trim());
                String description = fields[4].trim();
                BigDecimal amount = new BigDecimal(fields[5].trim());
                String category = (fields.length > 6) ? fields[6].trim() : "Outros";

                TransactionType type = amount.compareTo(BigDecimal.ZERO) < 0
                        ? TransactionType.EXPENSE
                        : TransactionType.INCOME;

                category = normalizeCategory(category);

                return new TransactionData(description, amount.abs(), type, category, date, cardName);

            } else if (format == CsvFormat.PORTUGUESE) {
                // Formato: Data,Descrição,Valor,Categoria
                if (fields.length < 4) return null;

                LocalDate date = parseDate(fields[0].trim());
                String description = fields[1].trim();
                BigDecimal amount = new BigDecimal(fields[2].trim());
                String category = fields[3].trim();

                // Se valor é negativo, é despesa
                TransactionType type = amount.compareTo(BigDecimal.ZERO) < 0
                        ? TransactionType.EXPENSE
                        : TransactionType.INCOME;

                // Normalizar categoria
                category = normalizeCategory(category);

                return new TransactionData(description, amount.abs(), type, category, date);

            } else if (format == CsvFormat.ENGLISH) {
                // Formato: description,amount,category,date,type
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

                return new TransactionData(description, amount, type, category, date);
            }

            return null;
        } catch (Exception e) {
            System.err.println("[CSV] Erro ao parsear linha: " + line + " - " + e.getMessage());
            return null;
        }
    }

    /**
     * Parse data em múltiplos formatos
     */
    private LocalDate parseDate(String dateStr) {
        try {
            // Tenta formato ISO: 2025-01-02
            return LocalDate.parse(dateStr);
        } catch (Exception e1) {
            try {
                // Tenta formato brasileiro: 02/01/2025
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
                return LocalDate.parse(dateStr, formatter);
            } catch (Exception e2) {
                // Fallback: data atual
                return LocalDate.now();
            }
        }
    }

    /**
     * Normaliza categorias em português para inglês
     */
    private String normalizeCategory(String category) {
        if (category == null) return "Other";

        String c = category.toLowerCase();
        if (c.contains("transporte")) return "Transport";
        if (c.contains("alimentação") || c.contains("alimentacao")) return "Food";
        if (c.contains("entretenimento")) return "Entertainment";
        if (c.contains("saúde") || c.contains("saude")) return "Health";
        if (c.contains("compras")) return "Shopping";
        if (c.contains("casa")) return "Housing";
        if (c.contains("vestuário") || c.contains("vestuario")) return "Clothing";

        return category;
    }

    /**
     * Categoriza por palavras-chave simples
     */
    private String categorizeDescription(String description, TransactionType type) {
        String d = description == null ? "" : description.toLowerCase();
        if (type == TransactionType.INCOME) {
            if (d.contains("salary") || d.contains("salário") || d.contains("payroll")) return "Income";
            if (d.contains("bonus") || d.contains("bônus")) return "Income";
            return "Income";
        } else {
            if (d.contains("uber") || d.contains("99") || d.contains("lyft")) return "Transport";
            if (d.contains("ifood") || d.contains("ubereats") || d.contains("restaurante") || d.contains("pizza")) return "Food";
            if (d.contains("mercado") || d.contains("supermarket") || d.contains("carrefour") || d.contains("pão de açúcar")) return "Groceries";
            if (d.contains("netflix") || d.contains("spotify") || d.contains("youtube") || d.contains("prime")) return "Entertainment";
            if (d.contains("zara") || d.contains("renner") || d.contains("c&a") || d.contains("shein")) return "Clothing";
            if (d.contains("academia") || d.contains("gym") || d.contains("smartfit")) return "Health";
            if (d.contains("amazon") || d.contains("mercado livre") || d.contains("shopee")) return "Shopping";
            if (d.contains("casa") || d.contains("home") || d.contains("aluguel") || d.contains("rent")) return "Housing";
            return "Other";
        }
    }

    // ========== Classes auxiliares ==========

    private enum CsvFormat {
        PORTUGUESE,  // Data,Descrição,Valor,Categoria
        ENGLISH,     // description,amount,category,date,type
        FULL_EXPORT, // Nome,CPF Final,Cartão,Data,Descrição,Valor,Categoria
        UNKNOWN
    }

    private static class TransactionData {
        String description;
        BigDecimal amount;
        TransactionType type;
        String category;
        LocalDate date;
        String cardName;

        TransactionData(String description, BigDecimal amount, TransactionType type, String category, LocalDate date) {
            this(description, amount, type, category, date, null);
        }

        TransactionData(String description, BigDecimal amount, TransactionType type, String category, LocalDate date, String cardName) {
            this.description = description;
            this.amount = amount;
            this.type = type;
            this.category = category;
            this.date = date;
            this.cardName = cardName;
        }
    }
}