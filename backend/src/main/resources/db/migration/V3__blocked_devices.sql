-- Trust & safety (M4): devices banned along with a blocked identity, so a blocked user can't
-- simply re-register from the same phone with a new number.
CREATE TABLE blocked_devices (
    device_id  text PRIMARY KEY,
    blocked_at timestamptz NOT NULL DEFAULT now()
);
