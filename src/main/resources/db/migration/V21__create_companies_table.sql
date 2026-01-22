CREATE TABLE IF NOT EXISTS companies (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    document VARCHAR(255),
    description VARCHAR(255),
    owner_id UUID NOT NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

ALTER TABLE companies
    ADD COLUMN IF NOT EXISTS id UUID;

ALTER TABLE companies
    ADD COLUMN IF NOT EXISTS name VARCHAR(255);

ALTER TABLE companies
    ADD COLUMN IF NOT EXISTS document VARCHAR(255);

ALTER TABLE companies
    ADD COLUMN IF NOT EXISTS description VARCHAR(255);

ALTER TABLE companies
    ADD COLUMN IF NOT EXISTS owner_id UUID;

ALTER TABLE companies
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP;

ALTER TABLE companies
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints tc
        WHERE tc.constraint_type = 'PRIMARY KEY'
          AND tc.table_name = 'companies'
          AND tc.table_schema = 'public'
    ) THEN
        ALTER TABLE companies
            ADD CONSTRAINT companies_pkey PRIMARY KEY (id);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        JOIN pg_class t ON t.oid = c.conrelid
        JOIN pg_namespace n ON n.oid = t.relnamespace
        WHERE c.conname = 'companies_owner_id_fkey'
          AND t.relname = 'companies'
          AND n.nspname = 'public'
    ) THEN
        ALTER TABLE companies
            ADD CONSTRAINT companies_owner_id_fkey
            FOREIGN KEY (owner_id) REFERENCES persons(id) ON DELETE CASCADE;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_companies_owner_id ON companies(owner_id);
