ALTER TABLE financial_transactions
  ADD COLUMN IF NOT EXISTS purchase_date DATE;

CREATE INDEX IF NOT EXISTS idx_tx_person_purchase_date ON financial_transactions(person_id, purchase_date);
