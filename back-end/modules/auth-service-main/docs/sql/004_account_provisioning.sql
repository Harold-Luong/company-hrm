-- Additive migration. Run as auth_user before starting the updated Auth.
BEGIN;

ALTER TABLE users ADD COLUMN IF NOT EXISTS activation_pending BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_pending_inactive;
ALTER TABLE users ADD CONSTRAINT users_pending_inactive CHECK (NOT activation_pending OR NOT is_active);
CREATE TABLE IF NOT EXISTS account_provisioning_results (
    request_id UUID PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    employee_id UUID NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    result_payload TEXT NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS account_link_versions (
    employee_id UUID PRIMARY KEY,
    version BIGINT NOT NULL CHECK (version > 0)
);

CREATE TABLE IF NOT EXISTS event_outbox (
    event_id UUID PRIMARY KEY,
    topic VARCHAR(200) NOT NULL,
    message_key VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    claim_token UUID,
    lease_until TIMESTAMP WITH TIME ZONE,
    sent_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX IF NOT EXISTS event_outbox_pending_idx ON event_outbox (sent_at, next_attempt_at);

COMMIT;
