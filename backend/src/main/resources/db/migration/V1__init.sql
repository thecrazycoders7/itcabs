-- ITCABS core schema (M1). PostGIS optional; core dispatch works without it.
-- Enums as CHECK constraints (simple, portable) rather than PG enum types.

CREATE TABLE users (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    phone       TEXT NOT NULL UNIQUE,
    role        TEXT NOT NULL CHECK (role IN ('COORDINATOR','DRIVER')),
    name        TEXT NOT NULL DEFAULT '',
    status      TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','BLOCKED')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE driver_profiles (
    user_id         BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    vehicle_type    TEXT NOT NULL,
    vehicle_reg     TEXT NOT NULL,
    -- Aadhaar is NEVER stored raw (ADR-0006): reference token + masked display only.
    aadhaar_ref     TEXT,
    aadhaar_masked  TEXT,
    rc_number_masked TEXT,
    photo_url       TEXT,
    kyc_status      TEXT NOT NULL DEFAULT 'PENDING' CHECK (kyc_status IN ('PENDING','VERIFIED','REJECTED')),
    verified_at     TIMESTAMPTZ,
    verified_by     BIGINT REFERENCES users(id)
);

CREATE TABLE jobs (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    coordinator_id  BIGINT NOT NULL REFERENCES users(id),
    office          TEXT NOT NULL,
    shift           TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE legs (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    job_id        BIGINT NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    coordinator_id BIGINT NOT NULL REFERENCES users(id),
    pickup        TEXT NOT NULL,
    drop_point    TEXT NOT NULL,
    area          TEXT NOT NULL DEFAULT '',
    time_window   TEXT NOT NULL DEFAULT '',
    vehicle_type  TEXT NOT NULL DEFAULT '',
    fare_paise    BIGINT NOT NULL CHECK (fare_paise >= 0),   -- money in paise, never float
    seats         INT NOT NULL DEFAULT 1 CHECK (seats >= 1),
    status        TEXT NOT NULL DEFAULT 'OPEN'
                   CHECK (status IN ('OPEN','CLAIMED','CONFIRMED','COMPLETED','CANCELLED')),
    claimed_by    BIGINT REFERENCES users(id),
    claimed_at    TIMESTAMPTZ,
    version       INT NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Feed query: open legs, filterable by area/vehicle. Partial index keeps it hot.
CREATE INDEX idx_legs_open ON legs (area, vehicle_type) WHERE status = 'OPEN';
CREATE INDEX idx_legs_claimed_by ON legs (claimed_by);
CREATE INDEX idx_legs_coordinator ON legs (coordinator_id);

-- Immutable audit of every claim attempt outcome (trust/compliance).
CREATE TABLE claims_audit (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    leg_id      BIGINT NOT NULL REFERENCES legs(id),
    driver_id   BIGINT NOT NULL REFERENCES users(id),
    outcome     TEXT NOT NULL CHECK (outcome IN ('WON','LOST')),
    at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Per-trip ratings (immutable records; aggregate is derived, not stored redundantly).
CREATE TABLE ratings (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    leg_id      BIGINT NOT NULL REFERENCES legs(id),
    rater_id    BIGINT NOT NULL REFERENCES users(id),
    ratee_id    BIGINT NOT NULL REFERENCES users(id),
    stars       INT NOT NULL CHECK (stars BETWEEN 1 AND 5),
    review      TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (leg_id, rater_id)
);

-- Revocable refresh-token sessions (ADR-0005).
CREATE TABLE device_sessions (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    refresh_token_hash  TEXT NOT NULL,
    device              TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at          TIMESTAMPTZ
);

-- One-time OTP challenges (dev: code is logged, not SMS'd).
CREATE TABLE otp_challenges (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    phone       TEXT NOT NULL,
    code_hash   TEXT NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_otp_phone ON otp_challenges (phone, created_at DESC);
