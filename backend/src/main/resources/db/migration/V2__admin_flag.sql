-- Admin RBAC (M1 tail). Admin is orthogonal to role (COORDINATOR/DRIVER), so it's a
-- flag, not a new role value. Admins are minted out-of-band only:
--   UPDATE users SET is_admin = true WHERE phone = '+91...';
-- No public path sets this — OTP signup can never make someone an admin.
ALTER TABLE users ADD COLUMN is_admin BOOLEAN NOT NULL DEFAULT false;
