-- Peer-to-peer carpooling: a member HOSTS a ride (drives their own car) and other members BOOK seats.
-- Coexists with the legs/company_jobs dispatch tables (kept for now; the app pivots to rides).
CREATE TABLE rides (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    host_id      BIGINT NOT NULL REFERENCES users(id),
    origin       text NOT NULL,
    origin_lat   double precision,
    origin_lng   double precision,
    destination  text NOT NULL,
    dest_lat     double precision,
    dest_lng     double precision,
    depart_at    timestamptz NOT NULL,
    total_seats  int    NOT NULL CHECK (total_seats BETWEEN 1 AND 6),
    price_paise  bigint NOT NULL CHECK (price_paise >= 0),   -- per seat, cost-share
    car_model    text NOT NULL DEFAULT '',
    women_only   boolean NOT NULL DEFAULT false,
    notes        text NOT NULL DEFAULT '',
    status       text NOT NULL DEFAULT 'OPEN'
                   CHECK (status IN ('OPEN','FULL','STARTED','COMPLETED','CANCELLED')),
    created_at   timestamptz NOT NULL DEFAULT now(),
    version      int NOT NULL DEFAULT 0
);
CREATE INDEX idx_rides_open ON rides (depart_at) WHERE status = 'OPEN';
CREATE INDEX idx_rides_host ON rides (host_id);

CREATE TABLE ride_bookings (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ride_id     BIGINT NOT NULL REFERENCES rides(id) ON DELETE CASCADE,
    rider_id    BIGINT NOT NULL REFERENCES users(id),
    seats       int  NOT NULL DEFAULT 1 CHECK (seats >= 1),
    status      text NOT NULL DEFAULT 'CONFIRMED'
                  CHECK (status IN ('CONFIRMED','CANCELLED','COMPLETED')),
    pickup_otp  text,
    paid_at     timestamptz,
    created_at  timestamptz NOT NULL DEFAULT now(),
    UNIQUE (ride_id, rider_id)   -- one active booking per rider per ride
);
CREATE INDEX idx_ride_bookings_ride ON ride_bookings (ride_id);
CREATE INDEX idx_ride_bookings_rider ON ride_bookings (rider_id);
