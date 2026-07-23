-- Move identity to Supabase Auth (Google + email/password). We now trust Supabase-issued JWTs and
-- link each Supabase user to our domain user by auth_id (the Supabase user UUID). Phone becomes
-- optional profile data instead of the login key.
ALTER TABLE users ADD COLUMN auth_id text UNIQUE;
ALTER TABLE users ADD COLUMN email text;
ALTER TABLE users ALTER COLUMN phone DROP NOT NULL;
