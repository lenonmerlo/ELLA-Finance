-- Add Insights and Goals tables migration

-- Create insights table if not exists
CREATE TABLE IF NOT EXISTS insights (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(person_id),
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    category VARCHAR(100),
    severity VARCHAR(50) NOT NULL,
    actionable BOOLEAN NOT NULL DEFAULT FALSE,
    generated_at DATE,
    start_date DATE,
    end_date DATE,
    FOREIGN KEY (user_id) REFERENCES users(person_id) ON DELETE CASCADE
);

-- Create index for insights
CREATE INDEX IF NOT EXISTS idx_insight_user_date ON insights(user_id, generated_at);

-- Add missing columns to goals table
ALTER TABLE IF EXISTS goals ADD COLUMN IF NOT EXISTS category VARCHAR(100);
ALTER TABLE IF EXISTS goals ADD COLUMN IF NOT EXISTS savings_potential NUMERIC(19, 2);
ALTER TABLE IF EXISTS goals ADD COLUMN IF NOT EXISTS difficulty VARCHAR(50);
ALTER TABLE IF EXISTS goals ADD COLUMN IF NOT EXISTS timeframe VARCHAR(50);
ALTER TABLE IF EXISTS goals ADD COLUMN IF NOT EXISTS target_date DATE;

-- Create index for goals with status
CREATE INDEX IF NOT EXISTS idx_goal_user_status ON goals(owner_id, status);
