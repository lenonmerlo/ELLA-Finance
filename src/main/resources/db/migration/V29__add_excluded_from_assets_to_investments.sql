ALTER TABLE investments
    ADD COLUMN IF NOT EXISTS excluded_from_assets BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_investments_excluded_from_assets ON investments(excluded_from_assets);
