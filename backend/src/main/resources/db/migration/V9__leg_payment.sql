-- Cash settlement tracking (no gateway): coordinators pay drivers per trip, so record WHEN a
-- completed leg was settled. NULL = still owed. Lets both sides see dues at a glance.
ALTER TABLE legs ADD COLUMN paid_at timestamptz;
