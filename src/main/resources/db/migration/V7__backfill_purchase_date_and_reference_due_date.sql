-- Backfill purchase_date and align reference month to invoice due date
--
-- Goal:
-- - For invoice-uploaded transactions (linked in installments), preserve original line date in purchase_date
-- - Use due_date (installment due date / invoice due date) as the reference transaction_date used by the dashboard

-- 1) If purchase_date is missing, assume current transaction_date is the original purchase/line date
UPDATE financial_transactions ft
SET purchase_date = ft.transaction_date
FROM installments i
WHERE i.transaction_id = ft.id
  AND ft.purchase_date IS NULL;

-- 2) Set transaction_date to the installment due date (invoice due date)
UPDATE financial_transactions ft
SET transaction_date = i.due_date,
    due_date = COALESCE(ft.due_date, i.due_date)
FROM installments i
WHERE i.transaction_id = ft.id
  AND i.due_date IS NOT NULL
  AND (
    ft.transaction_date IS DISTINCT FROM i.due_date
    OR ft.due_date IS DISTINCT FROM i.due_date
  );
