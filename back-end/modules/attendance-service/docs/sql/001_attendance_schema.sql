-- Active: 1790243208324@@127.0.0.1@5432@attendance_db@public
-- Run as attendance_db owner. No cross-service database references.
BEGIN;
CREATE TABLE IF NOT EXISTS attendance_schedule_state (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    version BIGINT NOT NULL DEFAULT 0,
    revision BIGINT NOT NULL DEFAULT 0 CHECK (revision >= 0)
);
INSERT INTO attendance_schedule_state(id, version, revision)
SELECT 1, 0, 0 WHERE NOT EXISTS (SELECT 1 FROM attendance_schedule_state WHERE id = 1);

CREATE TABLE IF NOT EXISTS work_shifts (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    name VARCHAR(100) NOT NULL,
    definition TEXT NOT NULL,
    required_minutes INTEGER NOT NULL CHECK (required_minutes > 0 AND required_minutes < 1440),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE IF NOT EXISTS shift_revisions (
    id UUID PRIMARY KEY,
    shift_id UUID NOT NULL REFERENCES work_shifts(id),
    shift_version BIGINT NOT NULL,
    definition TEXT NOT NULL,
    active BOOLEAN NOT NULL,
    actor_user_id VARCHAR(255) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (shift_id, shift_version)
);
CREATE TABLE IF NOT EXISTS attendance_schedule_batches (
    id UUID PRIMARY KEY,
    schedule_revision BIGINT NOT NULL,
    actor_user_id VARCHAR(255) NOT NULL,
    request_body TEXT NOT NULL,
    replaced_rules TEXT NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE IF NOT EXISTS work_schedule_rules (
    id UUID PRIMARY KEY,
    employee_id UUID,
    batch_id UUID NOT NULL REFERENCES attendance_schedule_batches(id),
    shift_id UUID NOT NULL REFERENCES work_shifts(id),
    shift_version BIGINT NOT NULL,
    definition TEXT NOT NULL,
    weekday INTEGER NOT NULL CHECK (weekday BETWEEN 1 AND 7),
    effective_from DATE NOT NULL,
    effective_until DATE NOT NULL,
    CHECK (effective_from <= effective_until)
);
CREATE INDEX IF NOT EXISTS schedule_rule_lookup_idx ON work_schedule_rules(employee_id, weekday, effective_from, effective_until);

CREATE TABLE IF NOT EXISTS attendance_daily (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    employee_id UUID NOT NULL,
    work_date DATE NOT NULL,
    shift_id UUID NOT NULL REFERENCES work_shifts(id),
    shift_version BIGINT NOT NULL,
    shift_definition TEXT NOT NULL,
    employee_snapshot TEXT NOT NULL,
    leave_snapshot TEXT NOT NULL,
    holiday_snapshot TEXT NOT NULL,
    source_observed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    check_in TIMESTAMP WITH TIME ZONE,
    check_out TIMESTAMP WITH TIME ZONE,
    UNIQUE (employee_id, work_date),
    CHECK (check_out IS NULL OR (check_in IS NOT NULL AND check_out > check_in))
);
CREATE INDEX IF NOT EXISTS attendance_daily_date_idx ON attendance_daily(work_date, employee_id);
CREATE TABLE IF NOT EXISTS attendance_events (
    id UUID PRIMARY KEY,
    day_id UUID NOT NULL REFERENCES attendance_daily(id),
    event_type VARCHAR(30) NOT NULL CHECK (event_type IN ('CHECK_IN','CHECK_OUT')),
    event_at TIMESTAMP WITH TIME ZONE NOT NULL,
    method VARCHAR(30) NOT NULL CHECK (method = 'CORPORATE_NETWORK'),
    actor_user_id VARCHAR(255) NOT NULL,
    source_ip VARCHAR(100) NOT NULL
);
CREATE INDEX IF NOT EXISTS attendance_events_day_idx ON attendance_events(day_id, event_at);
CREATE TABLE IF NOT EXISTS attendance_operations (
    id UUID PRIMARY KEY,
    actor_user_id VARCHAR(255) NOT NULL,
    operation_type VARCHAR(50) NOT NULL,
    request_key VARCHAR(100) NOT NULL,
    request_body TEXT NOT NULL,
    response_body TEXT NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (actor_user_id, operation_type, request_key)
);

-- A configurable template only. HR must explicitly choose workdays and effective dates.
INSERT INTO work_shifts(id, version, name, definition, required_minutes, active, updated_at)
SELECT '00000000-0000-0000-0000-000000000001', 0, 'Company default',
    '{"name":"Company default","mode":"FIXED_SHIFT","timezone":"Asia/Ho_Chi_Minh","intervals":[{"period":"MORNING","start":"08:00","end":"12:00"},{"period":"AFTERNOON","start":"13:30","end":"17:30"}],"checkInFrom":"06:00","checkOutUntil":"22:00"}',
    480, TRUE, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM work_shifts WHERE id = '00000000-0000-0000-0000-000000000001');
INSERT INTO shift_revisions(id, shift_id, shift_version, definition, active, actor_user_id, occurred_at)
SELECT '00000000-0000-0000-0000-000000000001', id, version, definition, active, 'SYSTEM_SCHEMA', updated_at
FROM work_shifts WHERE id = '00000000-0000-0000-0000-000000000001'
    AND NOT EXISTS (SELECT 1 FROM shift_revisions WHERE id = '00000000-0000-0000-0000-000000000001');
COMMIT;
