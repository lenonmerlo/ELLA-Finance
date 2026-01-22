CREATE TABLE IF NOT EXISTS subscriptions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    status VARCHAR(50) NOT NULL,
    plan VARCHAR(50) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    auto_renew BOOLEAN NOT NULL DEFAULT FALSE
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        JOIN pg_class t ON t.oid = c.conrelid
        JOIN pg_namespace n ON n.oid = t.relnamespace
        WHERE c.conname = 'fk_subscriptions_user'
          AND t.relname = 'subscriptions'
          AND n.nspname = 'public'
    ) THEN
        ALTER TABLE subscriptions
            ADD CONSTRAINT fk_subscriptions_user
            FOREIGN KEY (user_id) REFERENCES users(person_id);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        JOIN pg_class t ON t.oid = c.conrelid
        JOIN pg_namespace n ON n.oid = t.relnamespace
        WHERE c.conname = 'uk_subscriptions_user_id'
          AND t.relname = 'subscriptions'
          AND n.nspname = 'public'
    ) THEN
        ALTER TABLE subscriptions
            ADD CONSTRAINT uk_subscriptions_user_id
            UNIQUE (user_id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_subscriptions_status ON subscriptions(status);
