package com.ella.backend.services.ai;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.ella.backend.dto.InvoiceStructuredData;
import com.ella.backend.enums.TransactionScope;
import com.ella.backend.enums.TransactionType;
import com.ella.backend.services.invoices.parsers.TransactionData;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseOutputItem;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class OpenAiStructuringService {

    @Value("${openai.api-key:}")
    private String apiKey;

    @Value("${openai.model:gpt-4-turbo}")
    private String model;

    @Value("${openai.max-tokens:2000}")
    private int maxTokens;

    @Value("${openai.temperature:0.3}")
    private double temperature;

    @Value("${openai.timeout-seconds:30}")
    private int timeoutSeconds;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private volatile OpenAIClient client;

    public InvoiceStructuredData structureInvoiceData(String ocrText) {
        String text = ocrText == null ? "" : ocrText.trim();
        if (text.isEmpty()) return null;

        String key = apiKey == null ? "" : apiKey.trim();
        if (key.isEmpty()) {
            log.info("[OpenAI] OPENAI_API_KEY ausente; pulando estruturação");
            return null;
        }

        OpenAIClient c = getOrCreateClient(key);

        String prompt = buildPrompt(text);
        log.info("[OpenAI] Iniciando estruturação de fatura... (ocrLen={} model={})", text.length(), safe(model));

        int maxAttempts = 3;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            long start = System.currentTimeMillis();
            try {
                // Using Responses API; we ask for raw JSON and parse ourselves.
                ResponseCreateParams params = ResponseCreateParams.builder()
                        .model(model)
                        .input(prompt)
                        .maxOutputTokens(maxTokens)
                        .temperature(temperature)
                        .build();

                Response response = c.responses().create(params);
                String output = extractOutputText(response);
                long elapsed = System.currentTimeMillis() - start;

                if (output == null || output.isBlank()) {
                    log.warn("[OpenAI] Resposta vazia (attempt={} elapsedMs={})", attempt, elapsed);
                    return null;
                }

                InvoiceStructuredData structured = parseStructuredJson(output);
                int txCount = structured != null && structured.getTransactions() != null ? structured.getTransactions().size() : 0;
                log.info("[OpenAI] Estruturação bem-sucedida: {} transações (elapsedMs={})", txCount, elapsed);
                return structured;
            } catch (Exception e) {
                long elapsed = System.currentTimeMillis() - start;
                log.error("[OpenAI] Erro ao estruturar (attempt={} elapsedMs={}): {}", attempt, elapsed, e.toString());

                if (attempt >= maxAttempts) {
                    return null;
                }

                sleepBackoff(attempt);
            }
        }

        return null;
    }

    private OpenAIClient getOrCreateClient(String key) {
        OpenAIClient current = client;
        if (current != null) return current;

        synchronized (this) {
            if (client != null) return client;
            client = OpenAIOkHttpClient.builder()
                    .apiKey(key)
                    .timeout(Duration.ofSeconds(Math.max(1, timeoutSeconds)))
                    // SDK already retries by default; we also wrap with our own retry.
                    .build();
            return client;
        }
    }

    private String buildPrompt(String ocrText) {
        return "Analise esta fatura de cartão de crédito extraída por OCR e estruture os dados em JSON.\n\n"
                + "IMPORTANTE:\n"
                + "1. Nome do cartão: Extraia exatamente como aparece (ex: VISA AETERNUM, SANTANDER UNIQUE VISA)\n"
                + "2. Titular: Nome completo do titular do cartão (NÃO o usuário logado)\n"
                + "3. Últimos 4 dígitos: Apenas os 4 últimos números (ex: 1234, 8854)\n"
                + "4. Data de vencimento: Formato ISO (yyyy-MM-dd)\n"
                + "5. Total: Número com 2 casas decimais\n"
                + "6. Transações: Lista com data, descrição, valor, categoria, tipo (EXPENSE/INCOME)\n\n"
                + "Responda APENAS com JSON válido, sem explicações adicionais.\n\n"
                + "Formato esperado:\n"
                + "{\n"
                + "  \"cardName\": \"string\",\n"
                + "  \"cardholder\": \"string\",\n"
                + "  \"lastFourDigits\": \"string\",\n"
                + "  \"dueDate\": \"yyyy-MM-dd\",\n"
                + "  \"totalAmount\": number,\n"
                + "  \"transactions\": [\n"
                + "    {\n"
                + "      \"date\": \"yyyy-MM-dd\",\n"
                + "      \"description\": \"string\",\n"
                + "      \"amount\": number,\n"
                + "      \"category\": \"string\",\n"
                + "      \"type\": \"EXPENSE|INCOME\"\n"
                + "    }\n"
                + "  ]\n"
                + "}\n\n"
                + "Texto OCR:\n"
                + ocrText;
    }

    private InvoiceStructuredData parseStructuredJson(String raw) throws Exception {
        String json = stripCodeFences(raw);
        OpenAiInvoiceJson parsed = objectMapper.readValue(json, OpenAiInvoiceJson.class);

        if (parsed == null) return null;

        InvoiceStructuredData out = new InvoiceStructuredData();
        out.setCardName(blankToNull(parsed.cardName));
        out.setCardholder(blankToNull(parsed.cardholder));
        out.setLastFourDigits(blankToNull(parsed.lastFourDigits));
        out.setTotalAmount(parsed.totalAmount);

        LocalDate due = parseIsoDate(parsed.dueDate);
        out.setDueDate(due);

        List<TransactionData> txs = new ArrayList<>();
        if (parsed.transactions != null) {
            for (OpenAiTx t : parsed.transactions) {
                if (t == null) continue;
                LocalDate date = parseIsoDate(t.date);
                String desc = blankToNull(t.description);
                BigDecimal amount = t.amount;
                String category = blankToNull(t.category);
                TransactionType type = parseType(t.type);

                if (desc == null || amount == null || type == null) continue;

                TransactionData td = new TransactionData(
                        desc,
                        amount.abs(),
                        type,
                        category != null ? category : "Outros",
                        date,
                        out.getCardName(),
                        TransactionScope.PERSONAL
                );
                if (due != null) td.setDueDate(due);
                if (out.getCardholder() != null) td.setCardholderName(out.getCardholder());
                if (out.getLastFourDigits() != null) td.lastFourDigits = out.getLastFourDigits();
                txs.add(td);
            }
        }

        out.setTransactions(txs);
        return out;
    }

    private static TransactionType parseType(String raw) {
        if (raw == null) return null;
        String v = raw.trim().toUpperCase();
        if (v.equals("EXPENSE")) return TransactionType.EXPENSE;
        if (v.equals("INCOME")) return TransactionType.INCOME;
        return null;
    }

    private static LocalDate parseIsoDate(String value) {
        if (value == null) return null;
        String v = value.trim();
        if (v.isEmpty()) return null;
        try {
            return LocalDate.parse(v);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String stripCodeFences(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.startsWith("```")) {
            // Remove leading ```json or ```
            int firstNewline = s.indexOf('\n');
            if (firstNewline >= 0) {
                s = s.substring(firstNewline + 1);
            }
            int lastFence = s.lastIndexOf("```");
            if (lastFence >= 0) {
                s = s.substring(0, lastFence);
            }
        }
        return s.trim();
    }

    private static String blankToNull(String value) {
        if (value == null) return null;
        String v = value.trim();
        return v.isEmpty() ? null : v;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String extractOutputText(Response response) {
        if (response == null) return "";
        try {
            StringBuilder sb = new StringBuilder();
            List<ResponseOutputItem> output = response.output();
            if (output == null || output.isEmpty()) return "";

            for (ResponseOutputItem item : output) {
                if (item == null) continue;
                item.message().ifPresent(message -> {
                    if (message.content() == null) return;
                    for (var content : message.content()) {
                        if (content == null) continue;
                        content.outputText().ifPresent(t -> {
                            String v = t.text();
                            if (v != null && !v.isBlank()) {
                                if (!sb.isEmpty()) sb.append('\n');
                                sb.append(v);
                            }
                        });
                    }
                });
            }

            return sb.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static void sleepBackoff(int attempt) {
        // Exponential-ish backoff: 400ms, 1200ms, 2800ms
        long ms;
        switch (attempt) {
            case 1 -> ms = 400;
            case 2 -> ms = 1200;
            default -> ms = 2800;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class OpenAiInvoiceJson {
        public String cardName;
        public String cardholder;
        public String lastFourDigits;
        public String dueDate;
        public BigDecimal totalAmount;
        public List<OpenAiTx> transactions;
    }

    private static final class OpenAiTx {
        public String date;
        public String description;
        public BigDecimal amount;
        public String category;
        public String type;
    }
}
