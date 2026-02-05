-- Safety migration: ensure soft-delete columns exist even if local Flyway history drifted.
--
-- Some dev databases may have V31 marked as applied with a different script.
-- This migration is idempotent and guarantees the columns/indexes required by the entities.

ALTER TABLE IF EXISTS invoices
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE IF EXISTS financial_transactions
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX IF NOT EXISTS idx_invoices_deleted_at ON invoices(deleted_at);
CREATE INDEX IF NOT EXISTS idx_financial_transactions_deleted_at ON financial_transactions(deleted_at);
