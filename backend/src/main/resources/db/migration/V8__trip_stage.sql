-- Live trip status: the driver reports progress (EN_ROUTE → ARRIVED → STARTED) so the coordinator
-- sees where the trip is without pinging "where are you?". NULL = claimed but not yet moving.
ALTER TABLE legs ADD COLUMN trip_stage text;
