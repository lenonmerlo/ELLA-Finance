-- Add critical-transaction flags (additive, safe defaults)
ALTER TABLE financial_transactions
    ADD COLUMN IF NOT EXISTS is_critical BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE financial_transactions
    ADD COLUMN IF NOT EXISTS critical_reason VARCHAR(64);

ALTER TABLE financial_transactions
    ADD COLUMN IF NOT EXISTS critical_reviewed BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE financial_transactions
    ADD COLUMN IF NOT EXISTS critical_reviewed_at TIMESTAMP;

-- Helpful indexes for listing/filters
CREATE INDEX IF NOT EXISTS idx_tx_person_critical_unreviewed_date
    ON financial_transactions (person_id, is_critical, critical_reviewed, transaction_date);

CREATE INDEX IF NOT EXISTS idx_tx_person_critical_reason_date
    ON financial_transactions (person_id, critical_reason, transaction_date);
