-- Reliability & accountability (the core Telegram-killer): track a driver's completed trips and
-- no-shows so coordinators can trust who they're handing a shift to, and ghosting has consequences.
ALTER TABLE driver_profiles ADD COLUMN trips_completed int NOT NULL DEFAULT 0;
ALTER TABLE driver_profiles ADD COLUMN no_shows       int NOT NULL DEFAULT 0;
