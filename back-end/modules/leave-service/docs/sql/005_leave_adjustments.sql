-- Apply to existing databases before starting the updated Leave service. Safe to rerun.
BEGIN;
CREATE TABLE IF NOT EXISTS leave_adjustments (
    id UUID PRIMARY KEY,
    request_id UUID NOT NULL REFERENCES leave_requests(id),
    requester_user_id VARCHAR(255) NOT NULL,
    cancel_all BOOLEAN NOT NULL,
    original_start DATE NOT NULL,
    original_end DATE NOT NULL,
    original_period VARCHAR(20) NOT NULL,
    original_units INTEGER NOT NULL CHECK (original_units > 0),
    proposed_start DATE,
    proposed_end DATE,
    proposed_period VARCHAR(20),
    proposed_units INTEGER NOT NULL CHECK (proposed_units >= 0 AND proposed_units < original_units),
    reason VARCHAR(2000) NOT NULL CHECK (length(btrim(reason)) > 0),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','APPROVED','REJECTED','CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_by VARCHAR(255),
    reviewed_at TIMESTAMPTZ,
    review_note VARCHAR(2000),
    CHECK ((cancel_all AND proposed_units = 0 AND proposed_start IS NULL AND proposed_end IS NULL AND proposed_period IS NULL)
        OR (NOT cancel_all AND proposed_units > 0 AND proposed_start IS NOT NULL AND proposed_end IS NOT NULL
            AND proposed_period IS NOT NULL AND proposed_period IN ('FULL_DAY','MORNING','AFTERNOON') AND proposed_end >= proposed_start
            AND proposed_start >= original_start AND proposed_end <= original_end
            AND (proposed_period = 'FULL_DAY' OR proposed_start = proposed_end))),
    CHECK (status NOT IN ('APPROVED','REJECTED') OR
        (reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL AND reviewed_by <> requester_user_id)),
    CHECK (status <> 'REJECTED' OR (review_note IS NOT NULL AND length(btrim(review_note)) > 0))
);
CREATE UNIQUE INDEX IF NOT EXISTS leave_adjustments_pending_idx ON leave_adjustments(request_id) WHERE status = 'PENDING';
CREATE INDEX IF NOT EXISTS leave_adjustments_request_idx ON leave_adjustments(request_id, created_at DESC);
ALTER TABLE leave_request_history DROP CONSTRAINT IF EXISTS leave_request_history_action_check;
ALTER TABLE leave_request_history ADD CONSTRAINT leave_request_history_action_check CHECK
    (action IN ('SUBMITTED','APPROVED','REJECTED','CANCELLED',
        'ADJUSTMENT_SUBMITTED','ADJUSTMENT_APPROVED','ADJUSTMENT_REJECTED','ADJUSTMENT_CANCELLED'));
COMMIT;
