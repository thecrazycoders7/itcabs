-- Bring company_jobs to parity with legs: cash settlement + unclaimed-escalation guard.
ALTER TABLE company_jobs ADD COLUMN paid_at      timestamptz;
ALTER TABLE company_jobs ADD COLUMN escalated_at timestamptz;
