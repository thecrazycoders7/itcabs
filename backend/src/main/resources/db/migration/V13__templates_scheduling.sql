-- Corporate transport is a recurring roster, not one-off trips. Two mechanisms:
--  1. publish_at: a job can be scheduled — its legs stay hidden from the driver feed until then.
--  2. job_templates: save a route's shape once; re-post with one tap, or mark it recurring so a
--     daily sweep auto-posts it (kills the every-morning re-entry grind).
ALTER TABLE jobs ADD COLUMN publish_at timestamptz NOT NULL DEFAULT now();

CREATE TABLE job_templates (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    coordinator_id BIGINT NOT NULL REFERENCES users(id),
    name           text   NOT NULL,
    office         text   NOT NULL,
    shift          text   NOT NULL,
    vehicle_type   text   NOT NULL DEFAULT '',
    legs_json      jsonb  NOT NULL,                 -- [{pickup,drop,area,timeWindow,farePaise,seats,passengerName,passengerPhone}]
    recurring      boolean NOT NULL DEFAULT false,  -- auto-post once per day
    last_posted_on date,                            -- guard: recurring template posts at most once/day
    created_at     timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_templates_coord ON job_templates (coordinator_id);
