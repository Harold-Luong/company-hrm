-- Apply before starting the updated Attendance service. Safe to rerun.
BEGIN;
CREATE TABLE IF NOT EXISTS attendance_periods (
    period_month VARCHAR(7) PRIMARY KEY, version BIGINT NOT NULL DEFAULT 0, closed BOOLEAN NOT NULL,
    actor_user_id VARCHAR(255) NOT NULL, changed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    reason TEXT NOT NULL, report_json TEXT NOT NULL, source_json TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS attendance_period_history (
    id UUID PRIMARY KEY, actor_user_id VARCHAR(255) NOT NULL, occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    period_month VARCHAR(7) NOT NULL REFERENCES attendance_periods(period_month), period_version BIGINT NOT NULL,
    closed BOOLEAN NOT NULL, reason TEXT NOT NULL, report_json TEXT NOT NULL,
    UNIQUE(period_month, period_version)
);
COMMIT;
