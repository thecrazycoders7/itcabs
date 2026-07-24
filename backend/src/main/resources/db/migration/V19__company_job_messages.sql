-- Chat for multi-stop company jobs (coordinator ↔ claiming driver), same shape as leg_messages.
CREATE TABLE company_job_messages (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    job_id     BIGINT NOT NULL REFERENCES company_jobs(id) ON DELETE CASCADE,
    sender_id  BIGINT NOT NULL REFERENCES users(id),
    body       text   NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_company_job_messages_job ON company_job_messages (job_id, created_at);
