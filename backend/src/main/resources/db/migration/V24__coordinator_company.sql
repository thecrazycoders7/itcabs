-- Capture a coordinator's company + office once (at onboarding) so new jobs prefill it.
ALTER TABLE users ADD COLUMN company_name    text;
ALTER TABLE users ADD COLUMN office_address  text;
ALTER TABLE users ADD COLUMN office_lat       double precision;
ALTER TABLE users ADD COLUMN office_lng       double precision;
ALTER TABLE users ADD COLUMN office_place_id  text;
