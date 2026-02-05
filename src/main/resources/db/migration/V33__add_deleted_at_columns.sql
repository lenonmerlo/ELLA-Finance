-- Add soft-delete columns used by the entities.
--
-- NOTE: This is intentionally idempotent (IF NOT EXISTS) to safely fix dev databases
-- that may have Flyway history drift from earlier versions of V31/V32.

ALTER TABLE IF EXISTS invoices
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE IF EXISTS financial_transactions
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX IF NOT EXISTS idx_invoices_deleted_at ON invoices(deleted_at);
CREATE INDEX IF NOT EXISTS idx_financial_transactions_deleted_at ON financial_transactions(deleted_at);
