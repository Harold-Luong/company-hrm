-- Existing databases need this table before running the JPA outbox, even when
-- HRM_NOTIFICATIONS_ENABLED=false. New databases include it in 001. Safe to rerun.
CREATE TABLE IF NOT EXISTS notification_outbox (
    event_id UUID PRIMARY KEY,
    message_key VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    attempts INTEGER NOT NULL DEFAULT 0,
    sent_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS notification_outbox_pending_idx
    ON notification_outbox(next_attempt_at, created_at) WHERE sent_at IS NULL;
