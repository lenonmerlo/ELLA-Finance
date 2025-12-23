CREATE TABLE IF NOT EXISTS category_rule (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL,
  pattern VARCHAR(255) NOT NULL,
  category VARCHAR(255) NOT NULL,
  priority INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_category_rule_user_id ON category_rule(user_id);
CREATE INDEX IF NOT EXISTS idx_category_rule_user_pattern ON category_rule(user_id, pattern);
CREATE INDEX IF NOT EXISTS idx_category_rule_user_priority ON category_rule(user_id, priority);

CREATE TABLE IF NOT EXISTS category_feedback (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL,
  transaction_id UUID,
  suggested_category VARCHAR(255),
  chosen_category VARCHAR(255) NOT NULL,
  confidence NUMERIC(4,3),
  created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_category_feedback_user_id ON category_feedback(user_id);
CREATE INDEX IF NOT EXISTS idx_category_feedback_tx_id ON category_feedback(transaction_id);
CREATE INDEX IF NOT EXISTS idx_category_feedback_created_at ON category_feedback(created_at);
