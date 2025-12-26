-- Create credit_cards table (if it doesn't exist yet) and ensure we store cardholder name
-- This supports managing cards whose cardholder differs from the logged-in user.

CREATE TABLE IF NOT EXISTS credit_cards (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    brand VARCHAR(255) NOT NULL,
    last_four_digits VARCHAR(4),
    limit_amount NUMERIC NOT NULL,
    closing_day INT NOT NULL,
    due_day INT NOT NULL,
    owner_id UUID NOT NULL,
    cardholder_name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_credit_cards_owner FOREIGN KEY(owner_id) REFERENCES persons(id)
);

-- Backwards/forwards compatible: if the table already exists but column doesn't, add it.
ALTER TABLE IF EXISTS credit_cards
  ADD COLUMN IF NOT EXISTS cardholder_name VARCHAR(255);

-- Backfill cardholder_name for existing rows (best-effort)
UPDATE credit_cards
SET cardholder_name = COALESCE(cardholder_name, name)
WHERE cardholder_name IS NULL;

-- Make it non-null if possible
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name='credit_cards' AND column_name='cardholder_name'
  ) THEN
    BEGIN
      ALTER TABLE credit_cards ALTER COLUMN cardholder_name SET NOT NULL;
    EXCEPTION WHEN others THEN
      -- ignore if constraint can't be applied (e.g., existing nulls in non-postgres environments)
      NULL;
    END;
  END IF;
END $$;

-- Indexes
CREATE INDEX IF NOT EXISTS idx_card_owner ON credit_cards(owner_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_credit_card_unique ON credit_cards(owner_id, last_four_digits, brand);

-- Ensure invoices has card_id FK
ALTER TABLE IF EXISTS invoices
  ADD COLUMN IF NOT EXISTS card_id UUID;

DO $$
BEGIN
  -- Add FK only if it doesn't exist (name-based)
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'fk_invoices_card'
  ) THEN
    ALTER TABLE invoices
      ADD CONSTRAINT fk_invoices_card FOREIGN KEY(card_id) REFERENCES credit_cards(id);
  END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_invoice_card_month_year ON invoices(card_id, month, year);
