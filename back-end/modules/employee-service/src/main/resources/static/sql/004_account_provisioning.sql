-- Additive migration. Run as employee_user before enabling HRM_EVENTS_ENABLED.
BEGIN;

CREATE TABLE IF NOT EXISTS account_provisioning_requests (
    request_id UUID PRIMARY KEY,
    employee_id UUID NOT NULL REFERENCES employees(id),
    pending_employee_id UUID UNIQUE,
    email VARCHAR(255) NOT NULL,
    requested_by VARCHAR(255) NOT NULL,
    idempotency_key UUID NOT NULL,
    correlation_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED')),
    error_code VARCHAR(80),
    result_event_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT account_request_idempotency_key UNIQUE (requested_by, idempotency_key),
    CONSTRAINT account_request_pending CHECK (
        (status = 'PENDING' AND pending_employee_id IS NOT NULL AND pending_employee_id = employee_id)
        OR (status <> 'PENDING' AND pending_employee_id IS NULL)
    )
);
CREATE INDEX IF NOT EXISTS account_request_employee_idx ON account_provisioning_requests (employee_id);
CREATE TABLE IF NOT EXISTS provisioning_processed_events (
    event_id UUID PRIMARY KEY,
    request_id UUID NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS employee_account_versions (
    employee_id UUID PRIMARY KEY,
    version BIGINT NOT NULL CHECK (version >= 0)
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
