-- Run before starting the updated Employee service on an existing database.
-- Works with or without migration 002. Do not run 002 after this migration.
-- Neither value of has_account tells us the current Auth account status.
-- Existing employees therefore start as UNKNOWN and require reconciliation.
BEGIN;

ALTER TABLE employees
    ADD COLUMN IF NOT EXISTS account_status VARCHAR(30) NOT NULL DEFAULT 'UNKNOWN'
        CONSTRAINT chk_employee_account_status CHECK (
            account_status IN ('UNKNOWN', 'NOT_CREATED', 'PENDING_ACTIVATION', 'ACTIVE', 'DISABLED')
        );

-- This changes the default for future inserts, not existing UNKNOWN rows.
ALTER TABLE employees
    ALTER COLUMN account_status SET DEFAULT 'NOT_CREATED';

ALTER TABLE employees
    DROP COLUMN IF EXISTS has_account;

COMMIT;
