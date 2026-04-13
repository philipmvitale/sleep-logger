-- A future improvement I would add would be to refresh this table periodically, but not needed for this sample app.
CREATE TABLE timezones
(
    name TEXT PRIMARY KEY
);

INSERT INTO timezones (name)
SELECT name
FROM pg_timezone_names;
