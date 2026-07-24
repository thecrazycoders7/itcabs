-- Corporate job detail upgrades: company address (Places), pickup/drop time, AC category.
-- vehicle_type is now standardized to SEDAN / SUV (existing free-text rows are left as-is).
ALTER TABLE company_jobs ADD COLUMN office_address  text NOT NULL DEFAULT '';
ALTER TABLE company_jobs ADD COLUMN office_lat      double precision;
ALTER TABLE company_jobs ADD COLUMN office_lng      double precision;
ALTER TABLE company_jobs ADD COLUMN office_place_id text;
ALTER TABLE company_jobs ADD COLUMN pickup_time     text NOT NULL DEFAULT '';
ALTER TABLE company_jobs ADD COLUMN drop_time       text NOT NULL DEFAULT '';
ALTER TABLE company_jobs ADD COLUMN vehicle_ac      boolean NOT NULL DEFAULT true;
