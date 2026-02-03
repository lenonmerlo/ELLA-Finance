package com.ella.backend.services.goals.providers;

import java.util.List;

import com.ella.backend.entities.FinancialTransaction;
import com.ella.backend.entities.Goal;
import com.ella.backend.entities.Person;

public interface GoalProvider {

    enum GoalDataSource {
        FINANCIAL_TRANSACTIONS_ONLY,
        CASHFLOW_COMBINED
    }

    /**
     * Gera 0..N metas financeiras baseadas no histórico recente do usuário.
     */
    List<Goal> generateGoals(Person person, List<FinancialTransaction> recentTransactions);

    /**
     * Fonte de dados preferida pelo provider.
     *
     * Por padrão, usa apenas {@link FinancialTransaction}. Alguns providers (ex: fluxo de caixa, assinaturas)
     * podem optar por incluir transações de extrato bancário agregadas.
     */
    default GoalDataSource getDataSource() {
        return GoalDataSource.FINANCIAL_TRANSACTIONS_ONLY;
    }

    /**
     * Prioridade da meta (1 = alta, 5 = baixa). Usado para ordenar geração.
     */
    int getPriority();
}
