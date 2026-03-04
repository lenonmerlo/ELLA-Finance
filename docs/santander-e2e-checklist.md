# Santander E2E Checklist

## Pré-requisitos
- Extractor rodando com rota `/parse/santander`.
- Backend rodando com `ella.extractor.base-url` apontando para o extractor ativo.
- Log de debug do selector habilitado no backend.

## Passos de validação
1. Fazer upload de uma fatura Santander no fluxo normal da aplicação.
2. Verificar no log do backend que o parser escolhido foi `SantanderExtractorParser`.
3. Confirmar presença de log do cliente extractor:
   - `[EllaExtractorClient] Sending Santander PDF ... /parse/santander`
4. Conferir no resultado:
   - `dueDate` preenchida.
   - `transactions` com lançamentos dos dois cartões quando aplicável.
   - sem linhas de cabeçalho/boletos como transação.
5. Validar reconciliação:
   - `sum(signedTransactions)` ≈ `total` (diferença <= 0.01) quando a fatura estiver balanceada.

## Sinais de regressão
- Parser escolhido como `ItauPersonaliteInvoiceParser` ou outro parser não Santander.
- Apenas 1–3 transações capturadas em fatura com muitas linhas.
- Inclusão de "PAGAR SOMENTE NAS AGENCIAS..." ou linhas de boleto como transação.
- `dueDate` nula em fatura que contém vencimento explícito.
