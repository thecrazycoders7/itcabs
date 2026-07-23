-- Location relevance without a paid geocoder: a curated gazetteer of the Hyderabad tech-corridor
-- areas this pilot serves. Coordinators pick from this list; the feed joins on legs.area to get
-- coordinates and sorts by distance from the driver. Swap/augment with real geocoding (Places API)
-- when a Maps key + billing exist — the schema doesn't change, rows just get more precise.
CREATE TABLE areas (
    name text PRIMARY KEY,
    lat  double precision NOT NULL,
    lng  double precision NOT NULL
);

INSERT INTO areas (name, lat, lng) VALUES
    ('Gachibowli',         17.4401, 78.3489),
    ('Hitec City',         17.4435, 78.3772),
    ('Madhapur',           17.4483, 78.3915),
    ('Kondapur',           17.4622, 78.3568),
    ('Financial District', 17.4144, 78.3427),
    ('Nanakramguda',       17.4166, 78.3372),
    ('Manikonda',          17.4021, 78.3805),
    ('Kokapet',            17.3986, 78.3311),
    ('Kukatpally',         17.4849, 78.4138),
    ('Miyapur',            17.4924, 78.3818),
    ('Ameerpet',           17.4375, 78.4483),
    ('Begumpet',           17.4440, 78.4694),
    ('Secunderabad',       17.4399, 78.4983),
    ('Banjara Hills',      17.4156, 78.4347),
    ('Jubilee Hills',      17.4326, 78.4071),
    ('Uppal',              17.4056, 78.5591),
    ('LB Nagar',           17.3476, 78.5490),
    ('Shamshabad',         17.2403, 78.4294);
