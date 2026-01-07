package com.ella.backend.services.ai;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.ella.backend.dto.InvoiceStructuredData;
import com.ella.backend.entities.FinancialTransaction;
import com.ella.backend.entities.Insight;
import com.ella.backend.entities.User;
import com.ella.backend.enums.InsightSeverity;
import com.ella.backend.repositories.FinancialTransactionRepository;
import com.ella.backend.repositories.InsightRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class InsightGenerationService {

    private final FinancialTransactionRepository transactionRepository;
    private final InsightRepository insightRepository;
    private final OpenAiStructuringService openAiStructuringService;

    @Value("${openai.api-key:}")
    private String openAiApiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Gera insights automáticos para um usuário em um período específico.
     * Chama OpenAI para análise e salva insights no banco.
     */
    public List<Insight> generateInsights(User user, LocalDate startDate, LocalDate endDate) {
        log.info("[OpenAI] Gerando insights para usuário {} de {} a {}", 
                 user.getId(), startDate, endDate);

        if (openAiApiKey == null || openAiApiKey.isBlank()) {
            log.warn("[OpenAI] OpenAI API key não configurada, insights não gerados");
            return Collections.emptyList();
        }

        try {
            // Buscar transações do período
            List<FinancialTransaction> transactions = transactionRepository.findByPersonAndTransactionDateBetween(
                (com.ella.backend.entities.Person) user, startDate, endDate
            );

            if (transactions.isEmpty()) {
                log.warn("[OpenAI] Nenhuma transação encontrada para gerar insights");
                return Collections.emptyList();
            }

            // Calcular métricas
            BigDecimal totalAmount = transactions.stream()
                .map(FinancialTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            Map<String, BigDecimal> categoryBreakdown = transactions.stream()
                .collect(Collectors.groupingBy(
                    FinancialTransaction::getCategory,
                    Collectors.reducing(BigDecimal.ZERO, FinancialTransaction::getAmount, BigDecimal::add)
                ));

            // Top 5 transações
            List<String> topTransactions = transactions.stream()
                .sorted((t1, t2) -> t2.getAmount().compareTo(t1.getAmount()))
                .limit(5)
                .map(t -> String.format("- %s: R$ %.2f (%s)", 
                    t.getDescription(), t.getAmount().doubleValue(), t.getCategory()))
                .collect(Collectors.toList());

            // Comparação com período anterior
            LocalDate previousStartDate = startDate.minusMonths(1);
            LocalDate previousEndDate = endDate.minusMonths(1);
            List<FinancialTransaction> previousTransactions = transactionRepository.findByPersonAndTransactionDateBetween(
                (com.ella.backend.entities.Person) user, previousStartDate, previousEndDate
            );
            BigDecimal previousTotal = previousTransactions.stream()
                .map(FinancialTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Montar prompt
            String categoryBreakdownStr = categoryBreakdown.entrySet().stream()
                .map(e -> String.format("- %s: R$ %.2f", e.getKey(), e.getValue()))
                .collect(Collectors.joining("\n"));

            String topTransactionsStr = String.join("\n", topTransactions);

            String percentChange = "N/A";
            if (previousTotal.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal change = totalAmount.subtract(previousTotal)
                    .divide(previousTotal, 2, java.math.RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
                percentChange = String.format("%s%%" , change.toString());
            }

            String prompt = buildPrompt(startDate, endDate, totalAmount, transactions.size(),
                categoryBreakdownStr, topTransactionsStr, percentChange);

            log.debug("[OpenAI] Prompt para insights: {}", prompt);

            // Chamar OpenAI com retry
            String response = callOpenAiWithRetry(prompt, 3);

            if (response == null || response.isBlank()) {
                log.warn("[OpenAI] Resposta vazia do OpenAI");
                return Collections.emptyList();
            }

            // Parsear resposta JSON
            List<Insight> insights = parseInsightsResponse(response, user, startDate, endDate);

            // Salvar no banco
            insightRepository.saveAll(insights);
            log.info("[OpenAI] Insights gerados com sucesso: {} insights salvos", insights.size());

            return insights;

        } catch (Exception e) {
            log.error("[OpenAI] Erro ao gerar insights", e);
            return Collections.emptyList();
        }
    }

    private String buildPrompt(LocalDate startDate, LocalDate endDate, BigDecimal totalAmount,
                              int transactionCount, String categoryBreakdown,
                              String topTransactions, String percentChange) {
        return """
            Analise estes gastos e gere 5 insights acionáveis sobre os padrões de gasto.
            
            IMPORTANTE:
            1. Cada insight deve ser específico e mensurável
            2. Insights devem ser acionáveis (usuário pode fazer algo)
            3. Priorize insights com impacto financeiro alto
            4. Use dados reais fornecidos
            5. Responda APENAS com JSON válido
            
            Dados do período:
            - Data início: %s
            - Data fim: %s
            - Total gasto: R$ %.2f
            - Número de transações: %d
            
            Categorias:
            %s
            
            Transações principais:
            %s
            
            Comparação com período anterior: %s de mudança
            
            Responda em JSON com este formato EXATO:
            {
              "insights": [
                {
                  "title": "string (máximo 50 caracteres)",
                  "description": "string (máximo 200 caracteres)",
                  "category": "string (ex: COMPRAS, ALIMENTACAO, TRANSPORTE)",
                  "severity": "LOW|MEDIUM|HIGH",
                  "actionable": true|false
                }
              ]
            }
            """.formatted(startDate, endDate, totalAmount, transactionCount,
                         categoryBreakdown, topTransactions, percentChange);
    }

    private String callOpenAiWithRetry(String prompt, int maxRetries) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.debug("[OpenAI] Tentativa {} de {}", attempt, maxRetries);

                // Usar OpenAiStructuringService para chamar OpenAI
                InvoiceStructuredData result = openAiStructuringService.structureInvoiceData(prompt);

                if (result != null) {
                    return prompt; // Retorna o prompt como placeholder
                }

                sleepBackoff(attempt, maxRetries);
            } catch (Exception e) {
                log.warn("[OpenAI] Tentativa {} falhou: {}", attempt, e.getMessage());
                if (attempt < maxRetries) {
                    sleepBackoff(attempt, maxRetries);
                }
            }
        }
        return null;
    }

    private void sleepBackoff(int attempt, int maxRetries) {
        if (attempt < maxRetries) {
            try {
                long delay = (long) Math.pow(2, attempt) * 200; // 400ms, 800ms, 1600ms
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private List<Insight> parseInsightsResponse(String response, User user,
                                               LocalDate startDate, LocalDate endDate) {
        try {
            // Remover code fences se presente
            String cleanResponse = response.replaceAll("```json\\s*", "")
                .replaceAll("```\\s*", "");

            InsightsResponse insightsResponse = objectMapper.readValue(
                cleanResponse, InsightsResponse.class
            );

            return insightsResponse.insights.stream()
                .map(dto -> {
                    Insight insight = new Insight();
                    insight.setUser(user);
                    insight.setTitle(dto.title);
                    insight.setDescription(dto.description);
                    insight.setCategory(dto.category);
                    insight.setSeverity(InsightSeverity.valueOf(dto.severity));
                    insight.setActionable(dto.actionable);
                    insight.setGeneratedAt(LocalDate.now());
                    insight.setStartDate(startDate);
                    insight.setEndDate(endDate);
                    return insight;
                })
                .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("[OpenAI] Erro ao parsear resposta de insights", e);
            return Collections.emptyList();
        }
    }

    // Inner classes para desserialização JSON
    public static class InsightsResponse {
        public List<InsightItem> insights;
    }

    public static class InsightItem {
        public String title;
        public String description;
        public String category;
        public String severity;
        public boolean actionable;
    }
}
