ALTER TABLE password_reset_tokens
    ADD COLUMN IF NOT EXISTS used_at TIMESTAMP NULL;
