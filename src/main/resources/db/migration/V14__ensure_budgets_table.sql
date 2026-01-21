-- Ensure budgets table exists (backwards/forwards compatible)
--
-- Why: if an earlier V13 was applied with different contents (or was edited after being applied),
-- Flyway will not re-run it. This migration guarantees the required table exists for schema validation.

CREATE TABLE IF NOT EXISTS budgets (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,

    -- Input fields
    income NUMERIC(19, 2) NOT NULL,
    essential_fixed_cost NUMERIC(19, 2) NOT NULL,
    necessary_fixed_cost NUMERIC(19, 2) NOT NULL,
    variable_fixed_cost NUMERIC(19, 2) NOT NULL,
    investment NUMERIC(19, 2) NOT NULL,
    planned_purchase NUMERIC(19, 2) NOT NULL,
    protection NUMERIC(19, 2) NOT NULL,

    -- Calculated fields
    total NUMERIC(19, 2) NOT NULL,
    balance NUMERIC(19, 2) NOT NULL,

    -- 50/30/20 rule (percentages)
    necessities_percentage NUMERIC(5, 2) NOT NULL,
    desires_percentage NUMERIC(5, 2) NOT NULL,
    investments_percentage NUMERIC(5, 2) NOT NULL,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Add any missing columns safely
ALTER TABLE IF EXISTS budgets
  ADD COLUMN IF NOT EXISTS owner_id UUID;

ALTER TABLE IF EXISTS budgets
  ADD COLUMN IF NOT EXISTS income NUMERIC(19, 2),
  ADD COLUMN IF NOT EXISTS essential_fixed_cost NUMERIC(19, 2),
  ADD COLUMN IF NOT EXISTS necessary_fixed_cost NUMERIC(19, 2),
  ADD COLUMN IF NOT EXISTS variable_fixed_cost NUMERIC(19, 2),
  ADD COLUMN IF NOT EXISTS investment NUMERIC(19, 2),
  ADD COLUMN IF NOT EXISTS planned_purchase NUMERIC(19, 2),
  ADD COLUMN IF NOT EXISTS protection NUMERIC(19, 2),
  ADD COLUMN IF NOT EXISTS total NUMERIC(19, 2),
  ADD COLUMN IF NOT EXISTS balance NUMERIC(19, 2),
  ADD COLUMN IF NOT EXISTS necessities_percentage NUMERIC(5, 2),
  ADD COLUMN IF NOT EXISTS desires_percentage NUMERIC(5, 2),
  ADD COLUMN IF NOT EXISTS investments_percentage NUMERIC(5, 2),
  ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITH TIME ZONE,
  ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE;

-- FK constraint (name-based)
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'fk_budgets_owner'
  ) THEN
    ALTER TABLE budgets
      ADD CONSTRAINT fk_budgets_owner FOREIGN KEY(owner_id) REFERENCES persons(id);
  END IF;
END $$;

-- Enforce one budget per owner
CREATE UNIQUE INDEX IF NOT EXISTS idx_budgets_owner_unique ON budgets(owner_id);
