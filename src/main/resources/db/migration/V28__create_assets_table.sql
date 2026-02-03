CREATE TABLE IF NOT EXISTS assets (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL,
    purchase_value NUMERIC(19, 2),
    current_value NUMERIC(19, 2) NOT NULL,
    purchase_date DATE,
    is_synced_from_investment BOOLEAN NOT NULL DEFAULT FALSE,
    investment_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_assets_owner FOREIGN KEY(owner_id) REFERENCES persons(id),
    CONSTRAINT fk_assets_investment FOREIGN KEY(investment_id) REFERENCES investments(id),
    CONSTRAINT uq_assets_investment UNIQUE (investment_id)
);

CREATE INDEX IF NOT EXISTS idx_assets_owner ON assets(owner_id);
CREATE INDEX IF NOT EXISTS idx_assets_investment_id ON assets(investment_id);
