package com.ella.backend.services;

import org.springframework.stereotype.Service;

/**
 * Service para gerenciar dados de Movimentação C/C (Conta Corrente).
 * Atualmente retorna um placeholder. Será implementado com integrações de extratos bancários.
 */
@Service
public class DashboardBankStatementsService {

    /**
     * Busca os extratos bancários do usuário para um período específico.
     *
     * @param personId ID da pessoa
     * @param year Ano (opcional)
     * @param month Mês (opcional)
     * @return Dados dos extratos ou placeholder
     */
    public Object getBankStatements(Long personId, Integer year, Integer month) {
        // Placeholder: Implementação futura
        // Aqui será integrado com:
        // 1. Parsers de extratos bancários (BB, Itaú, C6, etc.)
        // 2. Modelos de dados (BankStatement, BankStatementTransaction)
        // 3. Cálculo de saldo total (Recebimentos - Despesas)

        return new Object() {
            public String status = "coming_soon";
            public String message = "Movimentação C/C em breve — será alimentado por extratos bancários";
            public Object data = null;
        };
    }
}
