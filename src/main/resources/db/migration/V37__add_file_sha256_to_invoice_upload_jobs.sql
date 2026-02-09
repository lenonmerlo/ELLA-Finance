ALTER TABLE invoice_upload_jobs
    ADD COLUMN IF NOT EXISTS file_sha256 VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_invoice_upload_jobs_person_sha_created
    ON invoice_upload_jobs (person_id, file_sha256, created_at DESC);
