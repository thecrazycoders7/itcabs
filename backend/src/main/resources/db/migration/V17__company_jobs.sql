-- Multi-stop corporate model (coexists with the single-leg `legs` system):
--   one company_job = one company + trip_type (PICKUP/DROP) + N ordered employee stops, ONE driver.
-- The driver claims/gets-assigned the whole job; each stop carries its own GPS + Google place + a
-- per-stop pickup OTP the employee gives to prove pickup.
CREATE TABLE company_jobs (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    coordinator_id BIGINT NOT NULL REFERENCES users(id),
    company_name   text   NOT NULL,
    trip_type      text   NOT NULL CHECK (trip_type IN ('PICKUP','DROP')),
    office         text   NOT NULL DEFAULT '',   -- the company endpoint (drop dest / pickup origin)
    vehicle_type   text   NOT NULL DEFAULT '',
    fare_paise     bigint NOT NULL CHECK (fare_paise >= 0),   -- flat, whole job
    status         text   NOT NULL DEFAULT 'OPEN'
                     CHECK (status IN ('OPEN','CLAIMED','CONFIRMED','COMPLETED','CANCELLED')),
    claimed_by     BIGINT REFERENCES users(id),
    claimed_at     timestamptz,
    publish_at     timestamptz NOT NULL DEFAULT now(),  -- scheduled jobs hidden from feed until then
    created_at     timestamptz NOT NULL DEFAULT now(),
    version        int    NOT NULL DEFAULT 0
);
CREATE INDEX idx_company_jobs_open ON company_jobs (status) WHERE status = 'OPEN';
CREATE INDEX idx_company_jobs_coord ON company_jobs (coordinator_id);

CREATE TABLE job_stops (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    job_id        BIGINT NOT NULL REFERENCES company_jobs(id) ON DELETE CASCADE,
    employee_name text   NOT NULL,
    address       text   NOT NULL DEFAULT '',
    lat           double precision,
    lng           double precision,
    place_id      text,                 -- Google Places place_id (when set via autocomplete)
    phone         text   NOT NULL DEFAULT '',
    stop_order    int    NOT NULL DEFAULT 0,
    pickup_otp    text,                 -- generated on claim; employee gives it to the driver
    picked_up_at  timestamptz           -- set when the driver confirms this stop
);
CREATE INDEX idx_job_stops_job ON job_stops (job_id, stop_order);
