-- pickup_otp: proof the driver actually reached the passenger. Generated when a leg is claimed;
--   the coordinator relays it to the employee, and the driver must enter it to start the trip.
-- escalated_at: set once when a sweep has already warned the coordinator about a stale OPEN leg,
--   so we alert about an unfilled trip at most once.
ALTER TABLE legs ADD COLUMN pickup_otp   text;
ALTER TABLE legs ADD COLUMN escalated_at timestamptz;
