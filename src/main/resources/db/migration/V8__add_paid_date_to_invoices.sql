-- Adds invoice-level paid date to support a simple Paid? toggle per invoice

ALTER TABLE IF EXISTS invoices
    ADD COLUMN IF NOT EXISTS paid_date date;

CREATE INDEX IF NOT EXISTS idx_invoices_paid_date
    ON invoices(paid_date);
