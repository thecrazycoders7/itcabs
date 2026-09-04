-- Gender captured at onboarding, used to enforce women-only carpool rides (safety).
ALTER TABLE users ADD COLUMN gender text;   -- MALE / FEMALE / OTHER / null
