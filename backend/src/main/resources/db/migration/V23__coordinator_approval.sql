-- Coordinators now require admin approval before they can post trips (trust & safety).
-- Default APPROVED so existing coordinators are grandfathered in; new coordinators onboard as PENDING.
ALTER TABLE users ADD COLUMN coordinator_status text NOT NULL DEFAULT 'APPROVED';
