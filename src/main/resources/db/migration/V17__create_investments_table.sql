CREATE TABLE IF NOT EXISTS investments (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL,
    initial_value NUMERIC(19, 2) NOT NULL,
    current_value NUMERIC(19, 2) NOT NULL,
    investment_date DATE NOT NULL,
    description VARCHAR(500),
    profitability NUMERIC(5, 2) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_investments_owner FOREIGN KEY(owner_id) REFERENCES persons(id)
);

CREATE INDEX IF NOT EXISTS idx_investments_owner ON investments(owner_id);
