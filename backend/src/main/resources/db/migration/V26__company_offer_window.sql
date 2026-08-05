-- Fairness offer-window for corporate multi-stop jobs (mirrors V25 for single legs):
-- during this window only clean-record drivers may claim. NULL = no window (legacy rows).
ALTER TABLE company_jobs ADD COLUMN offer_until timestamptz;
