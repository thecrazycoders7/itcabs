-- Live driver location for on-trip tracking. Latest point per driver (not a history trail — the
-- pilot only needs "where are they now"). Coordinator reads it for the active trip's driver.
ALTER TABLE driver_profiles ADD COLUMN last_lat     double precision;
ALTER TABLE driver_profiles ADD COLUMN last_lng     double precision;
ALTER TABLE driver_profiles ADD COLUMN last_loc_at  timestamptz;
