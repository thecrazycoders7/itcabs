-- Fairness offer-window: for the first N seconds after a leg is posted, only priority drivers
-- (clean record — no no-shows) can see and claim it; after offer_until it opens to everyone eligible.
-- NULL = no window (legacy rows / instantly open). Rewards reliable drivers without locking new ones
-- out (a clean-record new driver counts as priority; only drivers with past no-shows wait the window).
ALTER TABLE legs ADD COLUMN offer_until timestamptz;
