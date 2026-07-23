-- Drivers aren't always working. `available` gates new-trip pushes so an off-duty driver isn't
-- pinged at 2am. Defaults true so existing drivers keep getting alerts until they toggle off.
ALTER TABLE driver_profiles ADD COLUMN available boolean NOT NULL DEFAULT true;
