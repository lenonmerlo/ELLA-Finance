CREATE TABLE IF NOT EXISTS invoice_upload_jobs (
    id UUID PRIMARY KEY,
    person_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(255),
    password VARCHAR(255),
    due_date VARCHAR(32),
    file_bytes BYTEA NOT NULL,
    result_json JSONB,
    error_message VARCHAR(2000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    started_at TIMESTAMP WITH TIME ZONE,
    finished_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_invoice_upload_jobs_person FOREIGN KEY (person_id) REFERENCES persons(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_invoice_upload_jobs_person_created
    ON invoice_upload_jobs (person_id, created_at DESC);
