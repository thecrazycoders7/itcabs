-- When an admin rejects a driver's KYC, record why so the driver knows what to fix on resubmit.
ALTER TABLE driver_profiles ADD COLUMN rejection_reason text;
