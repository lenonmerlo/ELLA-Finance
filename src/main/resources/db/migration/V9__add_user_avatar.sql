-- Adiciona campos de avatar no usuário (persistência do upload do Settings)

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS avatar bytea;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS avatar_content_type varchar(100);

CREATE INDEX IF NOT EXISTS idx_users_has_avatar
    ON users ((avatar IS NOT NULL));
