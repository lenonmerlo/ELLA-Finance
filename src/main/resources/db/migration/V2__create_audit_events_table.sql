CREATE TABLE IF NOT EXISTS audit_events (
    id UUID PRIMARY KEY,
    timestamp TIMESTAMP NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    user_email VARCHAR(255),
    ip_address VARCHAR(45),
    action VARCHAR(100) NOT NULL,
    entity_id VARCHAR(255),
    entity_type VARCHAR(100),
    details JSONB,
    status VARCHAR(20) NOT NULL,
    error_message VARCHAR(1000)
);

CREATE INDEX IF NOT EXISTS idx_audit_user_id ON audit_events(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_timestamp ON audit_events(timestamp);
CREATE INDEX IF NOT EXISTS idx_audit_action ON audit_events(action);
CREATE INDEX IF NOT EXISTS idx_audit_entity_type ON audit_events(entity_type);