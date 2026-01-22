CREATE TABLE IF NOT EXISTS goals (
    id UUID PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description VARCHAR(255),
    target_amount NUMERIC(19, 2) NOT NULL,
    current_amount NUMERIC(19, 2) NOT NULL DEFAULT 0,
    deadline DATE,
    status VARCHAR(50) NOT NULL,
    owner_id UUID NOT NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        JOIN pg_class t ON t.oid = c.conrelid
        JOIN pg_namespace n ON n.oid = t.relnamespace
        WHERE c.conname = 'fk_goals_owner'
          AND t.relname = 'goals'
          AND n.nspname = 'public'
    ) THEN
        ALTER TABLE goals
            ADD CONSTRAINT fk_goals_owner
            FOREIGN KEY (owner_id) REFERENCES persons(id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_goal_owner_status ON goals(owner_id, status);
