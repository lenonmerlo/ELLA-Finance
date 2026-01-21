-- Create budgets table (one budget per person)

CREATE TABLE IF NOT EXISTS budgets (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,

    -- Input fields
    income NUMERIC(19, 2) NOT NULL,
    essential_fixed_cost NUMERIC(19, 2) NOT NULL,
    necessary_fixed_cost NUMERIC(19, 2) NOT NULL,
    variable_fixed_cost NUMERIC(19, 2) NOT NULL,
    investment NUMERIC(19, 2) NOT NULL,
    planned_purchase NUMERIC(19, 2) NOT NULL,
    protection NUMERIC(19, 2) NOT NULL,

    -- Calculated fields
    total NUMERIC(19, 2) NOT NULL,
    balance NUMERIC(19, 2) NOT NULL,

    -- 50/30/20 rule (percentages)
    necessities_percentage NUMERIC(5, 2) NOT NULL,
    desires_percentage NUMERIC(5, 2) NOT NULL,
    investments_percentage NUMERIC(5, 2) NOT NULL,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_budgets_owner FOREIGN KEY(owner_id) REFERENCES persons(id)
);

-- Enforce one budget per owner
CREATE UNIQUE INDEX IF NOT EXISTS idx_budgets_owner_unique ON budgets(owner_id);
