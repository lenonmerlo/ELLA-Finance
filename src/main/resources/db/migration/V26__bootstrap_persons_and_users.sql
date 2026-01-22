CREATE TABLE IF NOT EXISTS persons (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    phone VARCHAR(255),
    birth_date DATE,
    address VARCHAR(500),
    income NUMERIC(19, 2),
    language VARCHAR(32) NOT NULL DEFAULT 'PT_BR',
    plan VARCHAR(32) NOT NULL DEFAULT 'FREE',
    currency VARCHAR(16) NOT NULL DEFAULT 'BRL',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS users (
    person_id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(32) NOT NULL DEFAULT 'USER',
    CONSTRAINT fk_users_person FOREIGN KEY (person_id) REFERENCES persons(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS financial_transactions (
    id UUID PRIMARY KEY,
    person_id UUID NOT NULL,
    description VARCHAR(1000) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    type VARCHAR(50) NOT NULL,
    scope VARCHAR(20) NOT NULL DEFAULT 'PERSONAL',
    category VARCHAR(255) NOT NULL,
    transaction_date DATE NOT NULL,
    due_date DATE,
    paid_date DATE,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_tx_person FOREIGN KEY (person_id) REFERENCES persons(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_tx_person_date ON financial_transactions(person_id, transaction_date);
CREATE INDEX IF NOT EXISTS idx_tx_person_type_date ON financial_transactions(person_id, type, transaction_date);

CREATE TABLE IF NOT EXISTS invoices (
    id UUID PRIMARY KEY,
    card_id UUID,
    month INT NOT NULL,
    year INT NOT NULL,
    due_date DATE NOT NULL,
    total_amount NUMERIC(19, 2) NOT NULL DEFAULT 0,
    paid_amount NUMERIC(19, 2) NOT NULL DEFAULT 0,
    status VARCHAR(50) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS installments (
    id UUID PRIMARY KEY,
    number INT NOT NULL,
    total INT NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    due_date DATE NOT NULL,
    invoice_id UUID NOT NULL,
    transaction_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_installments_invoice FOREIGN KEY (invoice_id) REFERENCES invoices(id) ON DELETE CASCADE,
    CONSTRAINT fk_installments_transaction FOREIGN KEY (transaction_id) REFERENCES financial_transactions(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_installments_invoice_id ON installments(invoice_id);
CREATE INDEX IF NOT EXISTS idx_installments_transaction_id ON installments(transaction_id);

ALTER TABLE IF EXISTS financial_transactions
    ADD COLUMN IF NOT EXISTS scope VARCHAR(20) NOT NULL DEFAULT 'PERSONAL';
