-- Flyway V2: Enforce phone NOT NULL
-- Backfills existing null phones with empty string, then enforces the constraint

UPDATE users SET phone = '' WHERE phone IS NULL;

ALTER TABLE users ALTER COLUMN phone SET NOT NULL;
