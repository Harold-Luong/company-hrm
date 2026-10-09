-- Schema duy nhất cho Attendance trong giai đoạn phát triển: chạy trên database trống.
-- Khi thay đổi schema, tạo lại database rồi chạy toàn bộ file; không dùng để nâng cấp dữ liệu cũ.
-- Chạy bằng attendance_db owner. Không có tham chiếu chéo database.
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
    shift_id UUID NOT NULL,
    shift_version BIGINT NOT NULL,
    weekday INTEGER NOT NULL CHECK (weekday BETWEEN 1 AND 7),
    effective_from DATE NOT NULL,
    effective_until DATE NOT NULL,
    CHECK (effective_from <= effective_until),
    CONSTRAINT schedule_rule_revision_fk FOREIGN KEY (shift_id, shift_version)
        REFERENCES shift_revisions (shift_id, shift_version)
);
CREATE INDEX IF NOT EXISTS schedule_rule_lookup_idx ON work_schedule_rules(employee_id, weekday, effective_from, effective_until);

-- Corrections retain the proposal, the previously effective pair and its attendance version.
CREATE TABLE IF NOT EXISTS attendance_corrections (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    employee_id UUID NOT NULL,
    requester_user_id VARCHAR(255) NOT NULL,
    employee_code VARCHAR(255) NOT NULL,
    employee_name VARCHAR(255) NOT NULL,
    work_date DATE NOT NULL,
    shift_id UUID NOT NULL,
    shift_version BIGINT NOT NULL,
    base_record_version BIGINT CHECK (base_record_version >= 0),
    before_check_in TIMESTAMP WITH TIME ZONE,
    before_check_out TIMESTAMP WITH TIME ZONE,
    proposed_check_in TIMESTAMP WITH TIME ZONE NOT NULL,
    proposed_check_out TIMESTAMP WITH TIME ZONE NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING','APPROVED','REJECTED','CANCELLED')),
    active_slot VARCHAR(10),
    review_note VARCHAR(1000),
    reviewed_by VARCHAR(255),
    reviewed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CHECK (proposed_check_out > proposed_check_in),
    CHECK ((status = 'PENDING' AND active_slot IS NOT NULL AND active_slot = 'PENDING')
        OR (status <> 'PENDING' AND active_slot IS NULL)),
    UNIQUE (employee_id, work_date, active_slot),
    FOREIGN KEY (shift_id, shift_version) REFERENCES shift_revisions (shift_id, shift_version)
);
CREATE INDEX IF NOT EXISTS correction_owner_idx ON attendance_corrections(employee_id, created_at, id);
CREATE INDEX IF NOT EXISTS correction_inbox_idx ON attendance_corrections(status, created_at, id);
CREATE INDEX IF NOT EXISTS correction_revision_idx ON attendance_corrections(shift_id, shift_version);
CREATE TABLE IF NOT EXISTS attendance_correction_history (
    id UUID PRIMARY KEY,
    request_id UUID NOT NULL REFERENCES attendance_corrections(id),
    request_version BIGINT NOT NULL,
    action VARCHAR(30) NOT NULL,
    actor_user_id VARCHAR(255) NOT NULL,
    snapshot TEXT NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (request_id, request_version)
);

CREATE TABLE IF NOT EXISTS attendance_daily (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    employee_id UUID NOT NULL,
    work_date DATE NOT NULL,
    shift_id UUID NOT NULL,
    shift_version BIGINT NOT NULL,
    employee_snapshot TEXT NOT NULL,
    leave_snapshot TEXT NOT NULL,
    holiday_snapshot TEXT NOT NULL,
    source_observed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    check_in TIMESTAMP WITH TIME ZONE,
    check_out TIMESTAMP WITH TIME ZONE,
    corrected_check_in TIMESTAMP WITH TIME ZONE,
    corrected_check_out TIMESTAMP WITH TIME ZONE,
    correction_id UUID REFERENCES attendance_corrections(id),
    CHECK ((correction_id IS NULL AND corrected_check_in IS NULL AND corrected_check_out IS NULL)
        OR (correction_id IS NOT NULL AND corrected_check_in IS NOT NULL AND corrected_check_out IS NOT NULL
            AND corrected_check_out > corrected_check_in)),
    UNIQUE (employee_id, work_date),
    CHECK (check_out IS NULL OR (check_in IS NOT NULL AND check_out > check_in)),
    CONSTRAINT attendance_daily_revision_fk FOREIGN KEY (shift_id, shift_version)
        REFERENCES shift_revisions (shift_id, shift_version)
);
CREATE INDEX IF NOT EXISTS attendance_daily_date_idx ON attendance_daily(work_date, employee_id);
CREATE TABLE IF NOT EXISTS attendance_events (
    id UUID PRIMARY KEY,
    day_id UUID NOT NULL REFERENCES attendance_daily(id),
    event_type VARCHAR(30) NOT NULL CHECK (event_type IN ('CHECK_IN','CHECK_OUT')),
    event_at TIMESTAMP WITH TIME ZONE NOT NULL,
    method VARCHAR(30) NOT NULL CHECK (method = 'CORPORATE_NETWORK'),
    actor_user_id VARCHAR(255) NOT NULL,
    source_ip VARCHAR(100) NOT NULL,
    CONSTRAINT attendance_event_day_type_uq UNIQUE (day_id, event_type)
);
CREATE INDEX IF NOT EXISTS attendance_events_day_idx ON attendance_events(day_id, event_at);
CREATE TABLE IF NOT EXISTS attendance_operations (
    id UUID PRIMARY KEY,
    actor_user_id VARCHAR(255) NOT NULL,
    operation_type VARCHAR(50) NOT NULL,
    request_key VARCHAR(100) NOT NULL,
    request_body TEXT NOT NULL,
    response_body TEXT,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (actor_user_id, operation_type, request_key)
);


CREATE TABLE IF NOT EXISTS attendance_requests (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    employee_id UUID NOT NULL,
    requester_user_id VARCHAR(255) NOT NULL,
    employee_code VARCHAR(255) NOT NULL,
    employee_name VARCHAR(255) NOT NULL,
    work_date DATE NOT NULL,
    shift_id UUID NOT NULL,
    shift_version BIGINT NOT NULL,
    request_type VARCHAR(30) NOT NULL CHECK (
        request_type IN (
            'LATE_ARRIVAL',
            'EARLY_DEPARTURE'
        )
    ),
    period VARCHAR(20) NOT NULL CHECK (
        period IN ('MORNING', 'AFTERNOON')
    ),
    expected_time TIME NOT NULL,
    requested_minutes INTEGER NOT NULL CHECK (
        requested_minutes > 0
        AND requested_minutes < 1440
    ),
    reason VARCHAR(1000) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (
        status IN (
            'PENDING',
            'APPROVED',
            'REJECTED',
            'CANCELLED'
        )
    ),
    active_slot VARCHAR(10),
    review_note VARCHAR(1000),
    reviewed_by VARCHAR(255),
    reviewed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CHECK (
        (
            status IN ('PENDING', 'APPROVED')
            AND active_slot IS NOT NULL
            AND active_slot = 'ACTIVE'
        )
        OR (
            status IN ('REJECTED', 'CANCELLED')
            AND active_slot IS NULL
        )
    ),
    UNIQUE (
        employee_id,
        work_date,
        period,
        request_type,
        active_slot
    ),
    CONSTRAINT attendance_request_revision_fk FOREIGN KEY (shift_id, shift_version)
        REFERENCES shift_revisions (shift_id, shift_version)
);

CREATE INDEX IF NOT EXISTS attendance_request_owner_idx ON attendance_requests (employee_id, created_at, id);

CREATE INDEX IF NOT EXISTS attendance_request_inbox_idx ON attendance_requests (status, work_date);

CREATE TABLE IF NOT EXISTS attendance_request_history (
    id UUID PRIMARY KEY,
    request_id UUID NOT NULL REFERENCES attendance_requests (id),
    request_version BIGINT NOT NULL,
    action VARCHAR(30) NOT NULL,
    actor_user_id VARCHAR(255) NOT NULL,
    snapshot TEXT NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (request_id, request_version)
);

CREATE TABLE IF NOT EXISTS attendance_overtime_requests (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    employee_id UUID NOT NULL,
    requester_user_id VARCHAR(255) NOT NULL,
    employee_name VARCHAR(255) NOT NULL,
    work_date DATE NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (
        status IN (
            'PENDING',
            'APPROVED',
            'REJECTED',
            'CANCELLED'
        )
    ),
    review_note VARCHAR(1000),
    reviewed_by VARCHAR(255),
    reviewed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    check_in TIMESTAMP WITH TIME ZONE,
    check_out TIMESTAMP WITH TIME ZONE,
    check_in_ip VARCHAR(100),
    check_out_ip VARCHAR(100),
    CHECK (end_time > start_time),
    CHECK (
        check_in IS NULL
        OR status = 'APPROVED'
    ),
    CHECK (
        check_out IS NULL
        OR (
            check_in IS NOT NULL
            AND check_out > check_in
        )
    )
);

CREATE INDEX IF NOT EXISTS overtime_owner_date_idx ON attendance_overtime_requests (employee_id, work_date);

CREATE INDEX IF NOT EXISTS overtime_status_date_idx ON attendance_overtime_requests (status, work_date);

-- Index cho truy vấn theo khoảng ngày và lịch sử.
CREATE INDEX IF NOT EXISTS schedule_rule_range_idx
    ON work_schedule_rules (employee_id, effective_from, effective_until);

-- Audit history is paged by schedule revision.
CREATE INDEX IF NOT EXISTS schedule_batch_revision_idx
    ON attendance_schedule_batches (schedule_revision);

-- Pending/approved OT overlap checks filter employee, status and time interval.
CREATE INDEX IF NOT EXISTS overtime_overlap_idx
    ON attendance_overtime_requests (employee_id, status, start_time, end_time);

-- Existing unique (employee_id, work_date, ...) / owner-date indexes already
-- support attendance, active permission and OT range reads. Do not duplicate them.

-- Index phục vụ JOIN tới revision và kiểm tra khóa ngoại.
CREATE INDEX IF NOT EXISTS attendance_daily_revision_idx ON attendance_daily (shift_id, shift_version);
CREATE INDEX IF NOT EXISTS schedule_rule_revision_idx ON work_schedule_rules (shift_id, shift_version);
CREATE INDEX IF NOT EXISTS attendance_request_revision_idx ON attendance_requests (shift_id, shift_version);

-- DefaultScheduleInitializer assigns this template Monday-Friday on service startup
-- when no company schedule exists. Existing HR schedules and personal overrides are preserved.
INSERT INTO work_shifts(id, version, name, definition, required_minutes, active, updated_at)
SELECT '00000000-0000-0000-0000-000000000001', 0, 'Company default',
    '{"name":"Company default","mode":"FIXED_SHIFT","timezone":"Asia/Ho_Chi_Minh","intervals":[{"period":"MORNING","start":"08:00","end":"12:00"},{"period":"AFTERNOON","start":"13:30","end":"17:30"}],"checkInFrom":"06:00","checkOutUntil":"22:00"}',
    480, TRUE, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM work_shifts WHERE id = '00000000-0000-0000-0000-000000000001');
INSERT INTO shift_revisions(id, shift_id, shift_version, definition, active, actor_user_id, occurred_at)
SELECT '00000000-0000-0000-0000-000000000001', id, version, definition, active, 'SYSTEM_SCHEMA', updated_at
FROM work_shifts WHERE id = '00000000-0000-0000-0000-000000000001'
    AND NOT EXISTS (SELECT 1 FROM shift_revisions WHERE id = '00000000-0000-0000-0000-000000000001');

-- BEGIN POSTGRESQL ONLY
-- Test H2 đọc cùng file và bỏ khối đặc thù PostgreSQL này; verify-schema.sh kiểm tra đầy đủ.
CREATE INDEX IF NOT EXISTS attendance_operation_expiry_idx ON attendance_operations (operation_type, occurred_at)
    WHERE response_body IS NOT NULL;

-- Phiên bản ca chỉ được thêm mới; FK phía trên ngăn xóa phiên bản đang được dùng.
CREATE OR REPLACE FUNCTION attendance_reject_revision_update() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Shift revisions are immutable; create a new revision';
END $$;
DROP TRIGGER IF EXISTS shift_revision_immutable ON shift_revisions;
CREATE TRIGGER shift_revision_immutable BEFORE UPDATE ON shift_revisions
    FOR EACH ROW EXECUTE FUNCTION attendance_reject_revision_update();
-- END POSTGRESQL ONLY
COMMIT;
