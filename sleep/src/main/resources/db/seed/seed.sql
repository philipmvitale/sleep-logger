-- Seed two default users with predictable IDs.
-- User 1: America/New_York, will have 30 days of sleep history.
-- User 2: America/Los_Angeles, no sleep history.
--
-- Usage: psql -U user -d postgres -f seed.sql

INSERT INTO users (id, timezone) OVERRIDING SYSTEM VALUE
VALUES (1, 'America/New_York'),
       (2, 'America/Los_Angeles')
ON CONFLICT (id) DO NOTHING;

-- Advance the identity sequence past the seeded IDs so future inserts don't collide.
SELECT setval(pg_get_serial_sequence('users', 'id'), GREATEST(2, (SELECT MAX(id) FROM users)), true);

-- Generate 30 days of sleep logs for user 1 (America/New_York).
-- Each night: bed time between 21:00-23:30 ET, wake time between 05:30-07:30 ET the next morning.
-- Mood cycles through GOOD, OK, BAD to provide variety.
INSERT INTO sleep_logs (user_id, mood, bed_time, bed_timezone, wake_time, wake_timezone)
SELECT 1,
       (ARRAY ['GOOD','OK','BAD'])[1 + (g % 3)]::mood_type,
       -- Bed time: g days ago at ~22:00 ET, jittered by (g * 37 % 150) minutes
       (CURRENT_DATE - g * INTERVAL '1 day' + TIME '22:00')
           AT TIME ZONE 'America/New_York'
           + (g * 37 % 150) * INTERVAL '1 minute',
       'America/New_York',
       -- Wake time: (g-1) days ago at ~06:30 ET, jittered by (g * 53 % 120) minutes
       (CURRENT_DATE - (g - 1) * INTERVAL '1 day' + TIME '06:30')
           AT TIME ZONE 'America/New_York'
           + (g * 53 % 120) * INTERVAL '1 minute',
       'America/New_York'
FROM generate_series(1, 30) AS g;
