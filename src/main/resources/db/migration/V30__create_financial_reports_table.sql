CREATE TABLE IF NOT EXISTS financial_reports (
    id UUID PRIMARY KEY,
    person_id UUID NOT NULL,
    report_type VARCHAR(30) NOT NULL,
    title VARCHAR(200) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    reference_date DATE NOT NULL,
    data JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_financial_reports_person FOREIGN KEY(person_id) REFERENCES persons(id)
);

CREATE INDEX IF NOT EXISTS idx_financial_reports_person_created_at ON financial_reports(person_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_financial_reports_person_period ON financial_reports(person_id, period_start, period_end);
CREATE INDEX IF NOT EXISTS idx_financial_reports_type ON financial_reports(report_type);
