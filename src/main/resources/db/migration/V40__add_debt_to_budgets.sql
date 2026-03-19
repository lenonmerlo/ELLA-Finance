-- Add dedicated debt field to budgets to keep "Dívidas" separate from "Proteção"
ALTER TABLE IF EXISTS budgets
  ADD COLUMN IF NOT EXISTS debt NUMERIC(19, 2);

UPDATE budgets
SET debt = 0
WHERE debt IS NULL;

ALTER TABLE IF EXISTS budgets
  ALTER COLUMN debt SET DEFAULT 0,
  ALTER COLUMN debt SET NOT NULL;
