CREATE TABLE bank_statements (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    bank VARCHAR(50) NOT NULL,
    statement_date DATE NOT NULL,
    opening_balance DECIMAL(19,2) NOT NULL,
    closing_balance DECIMAL(19,2) NOT NULL,
    credit_limit DECIMAL(19,2) NOT NULL,
    available_limit DECIMAL(19,2) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(person_id)
);

CREATE TABLE bank_statement_transactions (
    id UUID PRIMARY KEY,
    bank_statement_id UUID NOT NULL,
    transaction_date DATE NOT NULL,
    description VARCHAR(500) NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    balance DECIMAL(19,2) NOT NULL,
    type VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    FOREIGN KEY (bank_statement_id) REFERENCES bank_statements(id)
);

CREATE INDEX idx_bank_statements_user_id ON bank_statements(user_id);
CREATE INDEX idx_bank_statements_statement_date ON bank_statements(statement_date);
CREATE INDEX idx_bank_statement_transactions_bank_statement_id ON bank_statement_transactions(bank_statement_id);
CREATE INDEX idx_bank_statement_transactions_transaction_date ON bank_statement_transactions(transaction_date);
