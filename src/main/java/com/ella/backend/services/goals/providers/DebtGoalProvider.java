package com.ella.backend.services.goals.providers;

import java.util.List;

import com.ella.backend.entities.FinancialTransaction;
import com.ella.backend.entities.Goal;
import com.ella.backend.entities.Person;
/**
 * Legacy provider (V1). Mantido apenas para compatibilidade de código.
 *
 * Não é registrado como componente Spring e não gera metas no V2.
 */
@Deprecated
public class DebtGoalProvider implements GoalProvider {

    @Override
    public List<Goal> generateGoals(Person person, List<FinancialTransaction> recentTransactions) {
        return List.of();
    }

    @Override
    public int getPriority() {
        return 99;
    }
}
