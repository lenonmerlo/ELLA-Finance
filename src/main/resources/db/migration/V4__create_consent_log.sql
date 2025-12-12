CREATE TABLE consent_log (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL,
  ip VARCHAR(64),
  contract_version VARCHAR(64) NOT NULL,
  accepted_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_consent_user_id ON consent_log(user_id);
CREATE INDEX idx_consent_accepted_at ON consent_log(accepted_at);
