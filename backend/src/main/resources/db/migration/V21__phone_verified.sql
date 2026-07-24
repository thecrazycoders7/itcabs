-- Enterprise KYC: even Google-auth drivers must verify a mobile number (Firebase Phone Auth) before
-- they can complete KYC or claim trips. We record the verified number + a flag on the user.
ALTER TABLE users ADD COLUMN phone_verified boolean NOT NULL DEFAULT false;
