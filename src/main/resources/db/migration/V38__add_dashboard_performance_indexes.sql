-- Dashboard performance indexes
-- NOTE: These indexes can take time and acquire locks on large tables.
-- Consider deploying during low-traffic windows.

-- Speed up (person_id + date range) queries for non-deleted rows
CREATE INDEX IF NOT EXISTS idx_ft_person_txdate_not_deleted
    ON financial_transactions (person_id, transaction_date DESC)
    WHERE deleted_at IS NULL;

-- Speed up category-filtered dashboard list queries (case-insensitive category)
CREATE INDEX IF NOT EXISTS idx_ft_person_lower_category_txdate_not_deleted
    ON financial_transactions (person_id, lower(category), transaction_date DESC)
    WHERE deleted_at IS NULL;
