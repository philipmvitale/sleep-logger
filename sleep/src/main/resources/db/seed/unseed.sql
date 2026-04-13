-- Remove seed data created by seed.sql.
-- Sleep logs for user 1 are deleted by CASCADE.
--
-- Usage: psql -U user -d postgres -f unseed.sql

DELETE
FROM users
WHERE id IN (1, 2);
