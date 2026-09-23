-- Historical migration, superseded by 003_replace_has_account_with_account_status.sql.
-- The current service needs migration 003; do not run this script after 003.
-- Existing accounts must be reconciled from Auth through a trusted integration.
ALTER TABLE employees
    ADD COLUMN IF NOT EXISTS has_account BOOLEAN NOT NULL DEFAULT FALSE;
