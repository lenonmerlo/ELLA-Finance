package com.ella.backend.services.goals.providers;

import java.util.List;

import com.ella.backend.entities.FinancialTransaction;
import com.ella.backend.entities.Goal;
import com.ella.backend.entities.Person;

public interface GoalProvider {

    /**
     * Gera 0..N metas financeiras baseadas no histórico recente do usuário.
     */
    List<Goal> generateGoals(Person person, List<FinancialTransaction> recentTransactions);

    /**
     * Prioridade da meta (1 = alta, 5 = baixa). Usado para ordenar geração.
     */
    int getPriority();
}
