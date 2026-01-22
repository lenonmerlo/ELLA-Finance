CREATE TABLE IF NOT EXISTS expenses (
    id UUID PRIMARY KEY,
    person_id UUID NOT NULL,
    description VARCHAR(255) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    transaction_date DATE NOT NULL,
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
        WHERE c.conname = 'fk_expenses_person'
          AND t.relname = 'expenses'
          AND n.nspname = 'public'
    ) THEN
        ALTER TABLE expenses
            ADD CONSTRAINT fk_expenses_person
            FOREIGN KEY (person_id) REFERENCES persons(id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_expense_person_date ON expenses(person_id, transaction_date);
