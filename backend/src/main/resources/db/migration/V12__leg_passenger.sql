-- The trip is about moving a specific employee, not an anonymous seat. Capture who, so the driver
-- can call "I'm at your gate". Optional (blank on legacy rows).
ALTER TABLE legs ADD COLUMN passenger_name  text NOT NULL DEFAULT '';
ALTER TABLE legs ADD COLUMN passenger_phone text NOT NULL DEFAULT '';
