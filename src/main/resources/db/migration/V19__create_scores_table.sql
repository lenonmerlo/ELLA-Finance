CREATE TABLE IF NOT EXISTS scores (
    id UUID PRIMARY KEY,
    person_id UUID NOT NULL,
    score_value INT NOT NULL,
    calculation_date DATE NOT NULL,
    credit_utilization_score INT NOT NULL,
    on_time_payment_score INT NOT NULL,
    spending_diversity_score INT NOT NULL,
    spending_consistency_score INT NOT NULL,
    credit_history_score INT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_scores_person FOREIGN KEY(person_id) REFERENCES persons(id)
);

CREATE INDEX IF NOT EXISTS idx_scores_person_id ON scores(person_id);
CREATE INDEX IF NOT EXISTS idx_scores_calculation_date ON scores(calculation_date);
