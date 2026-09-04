-- Extend ratings to cover carpool rides (was leg-only). One unified pool per user so host_rating
-- (avg over ratee_id) keeps working. A host rates multiple riders per ride, so uniqueness is
-- (ride, rater, ratee).
ALTER TABLE ratings ALTER COLUMN leg_id DROP NOT NULL;
ALTER TABLE ratings ADD COLUMN ride_id BIGINT REFERENCES rides(id);
CREATE UNIQUE INDEX idx_ratings_ride ON ratings (ride_id, rater_id, ratee_id) WHERE ride_id IS NOT NULL;
