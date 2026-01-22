CREATE TABLE IF NOT EXISTS payments (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    plan VARCHAR(50) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    provider VARCHAR(50) NOT NULL,
    provider_payment_id VARCHAR(255),
    provider_raw_status VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    paid_at TIMESTAMP
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        JOIN pg_class t ON t.oid = c.conrelid
        JOIN pg_namespace n ON n.oid = t.relnamespace
        WHERE c.conname = 'fk_payments_user'
          AND t.relname = 'payments'
          AND n.nspname = 'public'
    ) THEN
        ALTER TABLE payments
            ADD CONSTRAINT fk_payments_user
            FOREIGN KEY (user_id) REFERENCES users(person_id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_payments_user_id ON payments(user_id);
