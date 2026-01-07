package com.ella.backend.services.ai;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.ella.backend.entities.FinancialTransaction;
import com.ella.backend.entities.Goal;
import com.ella.backend.entities.Insight;
import com.ella.backend.entities.Person;
import com.ella.backend.enums.GoalDifficulty;
import com.ella.backend.enums.GoalStatus;
import com.ella.backend.enums.GoalTimeframe;
import com.ella.backend.repositories.FinancialTransactionRepository;
import com.ella.backend.repositories.GoalRepository;
import com.ella.backend.repositories.InsightRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoalSuggestionService {

    private final FinancialTransactionRepository transactionRepository;
    private final GoalRepository goalRepository;
    private final InsightRepository insightRepository;
    private final OpenAiStructuringService openAiStructuringService;

    @Value("${openai.api-key:}")
    private String openAiApiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Sugere metas realistas para um usuário baseado em seus insights e histórico.
     * Chama OpenAI e salva metas no banco.
     */
    public List<Goal> suggestGoals(Person owner) {
        log.info("[OpenAI] Sugerindo metas para usuário {}", owner.getId());

        if (openAiApiKey == null || openAiApiKey.isBlank()) {
            log.warn("[OpenAI] OpenAI API key não configurada, metas não sugeridas");
            return Collections.emptyList();
        }

        try {
            // Buscar insights recentes (últimos 3 meses)
            LocalDate threeMonthsAgo = LocalDate.now().minusMonths(3);
            List<Insight> recentInsights = insightRepository.findByUserAndGeneratedAtBetween(
                (com.ella.backend.entities.User) owner, threeMonthsAgo, LocalDate.now()
            );

            // Buscar histórico de transações (últimos 3 meses)
            List<FinancialTransaction> lastThreeMonths = transactionRepository.findByPersonAndTransactionDateAfter(
                owner, threeMonthsAgo
            );

            if (lastThreeMonths.isEmpty()) {
                log.warn("[OpenAI] Sem histórico de transações para sugerir metas");
                return Collections.emptyList();
            }

            // Calcular médias por categoria
            Map<String, BigDecimal> categoryAverages = calculateCategoryAverages(lastThreeMonths);

            // Calcular média mensal
            BigDecimal totalThreeMonths = lastThreeMonths.stream()
                .map(FinancialTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal averageMonthly = totalThreeMonths.divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);

            // Calcular tendências
            String trends = calculateTrends(lastThreeMonths);

            // Montar insights como string
            String insightsStr = recentInsights.stream()
                .map(i -> String.format("- %s: %s", i.getTitle(), i.getDescription()))
                .collect(Collectors.joining("\n"));

            String categoryAveragesStr = categoryAverages.entrySet().stream()
                .map(e -> String.format("- %s: R$ %.2f/mês", e.getKey(), e.getValue()))
                .collect(Collectors.joining("\n"));

            // Montar prompt
            String prompt = buildPrompt(insightsStr, averageMonthly, categoryAveragesStr, trends);

            log.debug("[OpenAI] Prompt para metas: {}", prompt);

            // Chamar OpenAI com retry
            String response = callOpenAiWithRetry(prompt, 3);

            if (response == null || response.isBlank()) {
                log.warn("[OpenAI] Resposta vazia do OpenAI");
                return Collections.emptyList();
            }

            // Parsear resposta JSON
            List<Goal> goals = parseGoalsResponse(response, owner);

            // Salvar no banco
            List<Goal> savedGoals = new ArrayList<>();
            for (Goal goal : goals) {
                goal.calculateTargetDate();
                savedGoals.add(goalRepository.save(goal));
            }

            log.info("[OpenAI] Metas sugeridas com sucesso: {} metas salvas", savedGoals.size());

            return savedGoals;

        } catch (Exception e) {
            log.error("[OpenAI] Erro ao sugerir metas", e);
            return Collections.emptyList();
        }
    }

    private Map<String, BigDecimal> calculateCategoryAverages(List<FinancialTransaction> transactions) {
        Map<String, BigDecimal> categoryTotals = new HashMap<>();

        for (FinancialTransaction t : transactions) {
            categoryTotals.merge(t.getCategory(), t.getAmount(), BigDecimal::add);
        }

        return categoryTotals.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue().divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP)
            ));
    }

    private String calculateTrends(List<FinancialTransaction> transactions) {
        // Agrupar por mês
        Map<String, BigDecimal> monthlyTotals = new TreeMap<>();

        for (FinancialTransaction t : transactions) {
            String yearMonth = t.getTransactionDate().getYear() + "-" +
                String.format("%02d", t.getTransactionDate().getMonthValue());
            monthlyTotals.merge(yearMonth, t.getAmount(), BigDecimal::add);
        }

        if (monthlyTotals.size() < 2) {
            return "Sem tendências suficientes";
        }

        List<BigDecimal> amounts = new ArrayList<>(monthlyTotals.values());
        BigDecimal firstMonth = amounts.get(0);
        BigDecimal lastMonth = amounts.get(amounts.size() - 1);

        if (firstMonth.compareTo(BigDecimal.ZERO) == 0) {
            return "Gastos estáveis";
        }

        BigDecimal change = lastMonth.subtract(firstMonth)
            .divide(firstMonth, 2, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));

        if (change.compareTo(BigDecimal.ZERO) > 0) {
            return String.format("Gastos aumentando (+%s%%)", change);
        } else if (change.compareTo(BigDecimal.ZERO) < 0) {
            return String.format("Gastos diminuindo (%s%%)", change);
        } else {
            return "Gastos estáveis";
        }
    }

    private String buildPrompt(String insights, BigDecimal averageMonthly,
                              String categoryAverages, String trends) {
        return """
            Com base nestes insights e histórico de gastos, sugira 5 metas realistas e acionáveis.
            
            IMPORTANTE:
            1. Metas devem ser realistas (não pedir redução de 90%%)
            2. Priorize metas com impacto financeiro alto
            3. Varie dificuldade (fácil, médio, difícil)
            4. Varie timeframe (1 semana, 2 semanas, 1 mês, 3 meses)
            5. Responda APENAS com JSON válido
            
            Insights recentes:
            %s
            
            Histórico (últimos 3 meses):
            - Média mensal: R$ %.2f
            - Categorias e médias:
            %s
            
            Tendências:
            %s
            
            Responda em JSON com este formato EXATO:
            {
              "goals": [
                {
                  "title": "string (máximo 60 caracteres)",
                  "description": "string (máximo 200 caracteres)",
                  "category": "string (ex: COMPRAS, ALIMENTACAO, TRANSPORTE)",
                  "targetAmount": number,
                  "currentAmount": number,
                  "savingsPotential": number,
                  "difficulty": "EASY|MEDIUM|HARD",
                  "timeframe": "ONE_WEEK|TWO_WEEKS|ONE_MONTH|THREE_MONTHS"
                }
              ]
            }
            """.formatted(insights, averageMonthly, categoryAverages, trends);
    }

    private String callOpenAiWithRetry(String prompt, int maxRetries) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.debug("[OpenAI] Tentativa {} de {} para sugerir metas", attempt, maxRetries);

                // Usar OpenAiStructuringService para chamar OpenAI
                var result = openAiStructuringService.structureInvoiceData(prompt);

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

    private List<Goal> parseGoalsResponse(String response, Person owner) {
        try {
            // Remover code fences se presente
            String cleanResponse = response.replaceAll("```json\\s*", "")
                .replaceAll("```\\s*", "");

            GoalsResponse goalsResponse = objectMapper.readValue(
                cleanResponse, GoalsResponse.class
            );

            return goalsResponse.goals.stream()
                .map(dto -> {
                    Goal goal = new Goal();
                    goal.setOwner(owner);
                    goal.setTitle(dto.title);
                    goal.setDescription(dto.description);
                    goal.setCategory(dto.category);
                    goal.setTargetAmount(new BigDecimal(dto.targetAmount));
                    goal.setCurrentAmount(new BigDecimal(dto.currentAmount));
                    goal.setSavingsPotential(new BigDecimal(dto.savingsPotential));
                    goal.setDifficulty(GoalDifficulty.valueOf(dto.difficulty));
                    goal.setTimeframe(GoalTimeframe.valueOf(dto.timeframe));
                    goal.setStatus(GoalStatus.ACTIVE);
                    goal.setDeadline(LocalDate.now());
                    goal.calculateTargetDate();
                    return goal;
                })
                .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("[OpenAI] Erro ao parsear resposta de metas", e);
            return Collections.emptyList();
        }
    }

    // Inner classes para desserialização JSON
    public static class GoalsResponse {
        public List<GoalItem> goals;
    }

    public static class GoalItem {
        public String title;
        public String description;
        public String category;
        public double targetAmount;
        public double currentAmount;
        public double savingsPotential;
        public String difficulty;
        public String timeframe;
    }
}
