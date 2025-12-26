ALTER TABLE financial_transactions
  ADD COLUMN IF NOT EXISTS trip_id UUID;

ALTER TABLE financial_transactions
  ADD COLUMN IF NOT EXISTS trip_subcategory VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_tx_person_trip_id ON financial_transactions(person_id, trip_id);
