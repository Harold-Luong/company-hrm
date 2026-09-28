-- Run before starting the updated Employee service on an existing database.
-- Works with or without migration 002. Do not run 002 after this migration.
-- Development default: employees without account status start as NOT_CREATED.
-- Rerunning also removes UNKNOWN from databases using the earlier version.
BEGIN;

ALTER TABLE employees
    ADD COLUMN IF NOT EXISTS account_status VARCHAR(30) NOT NULL DEFAULT 'NOT_CREATED';

ALTER TABLE employees
    DROP CONSTRAINT IF EXISTS chk_employee_account_status;

UPDATE employees SET account_status = 'NOT_CREATED' WHERE account_status = 'UNKNOWN';

ALTER TABLE employees
    ADD CONSTRAINT chk_employee_account_status CHECK (
        account_status IN ('NOT_CREATED', 'PENDING_ACTIVATION', 'ACTIVE', 'DISABLED')
    );

ALTER TABLE employees
    ALTER COLUMN account_status SET DEFAULT 'NOT_CREATED';

ALTER TABLE employees
    DROP COLUMN IF EXISTS has_account;

COMMIT;
