-- Fix insights table id column type from SERIAL to BIGSERIAL

-- Drop the old primary key and id sequence
ALTER TABLE insights DROP CONSTRAINT IF EXISTS insights_pkey;
DROP SEQUENCE IF EXISTS insights_id_seq CASCADE;

-- Add a new id column with BIGSERIAL
ALTER TABLE insights ADD COLUMN id_new BIGSERIAL PRIMARY KEY;

-- Copy data from old id to new id (if any exists)
UPDATE insights SET id_new = id WHERE id_new IS NULL;

-- Drop the old id column
ALTER TABLE insights DROP COLUMN id;

-- Rename new id column to id
ALTER TABLE insights RENAME COLUMN id_new TO id;

-- Recreate the index
DROP INDEX IF EXISTS idx_insight_user_date;
CREATE INDEX idx_insight_user_date ON insights(user_id, generated_at);
