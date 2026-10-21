#!/usr/bin/env bash
# Dùng PGHOST/PGPORT/PGUSER/PGPASSWORD/PGDATABASE; chỉ tạo và xóa schema thử riêng.
set -euo pipefail
service_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
test_schema="attendance_schema_test_$$"
log_file="$(mktemp /tmp/attendance-schema.XXXXXX.log)"
psql -X -v ON_ERROR_STOP=1 -q -c "CREATE SCHEMA $test_schema"
cleanup() {
    psql -X -v ON_ERROR_STOP=1 -q -c "DROP SCHEMA $test_schema CASCADE" >> "$log_file" 2>&1
}
trap cleanup EXIT
export PGOPTIONS="-c search_path=$test_schema"
# Tạo mới và chạy lại cùng schema phải giữ seed duy nhất.
for attempt in 1 2; do
    psql -X -v ON_ERROR_STOP=1 -q -f "$service_dir/docs/sql/001_attendance_schema.sql" >> "$log_file" 2>&1
done
# The upgrade script remains safe for databases already initialized from 001.
for attempt in 1 2; do
    psql -X -v ON_ERROR_STOP=1 -q -f "$service_dir/docs/sql/004_notification_outbox.sql" >> "$log_file" 2>&1
done
psql -X -v ON_ERROR_STOP=1 -q >> "$log_file" 2>&1 <<'SQL'
DO $$
BEGIN
    IF (SELECT count(*) FROM pg_tables WHERE schemaname=current_schema()) <> 16
        OR (SELECT count(*) FROM work_shifts) <> 1
        OR (SELECT count(*) FROM shift_revisions) <> 1
        OR (SELECT count(*) FROM attendance_schedule_state) <> 1 THEN
        RAISE EXCEPTION 'Incomplete schema or duplicate seed';
    END IF;
END $$;
INSERT INTO notification_outbox(event_id, message_key, payload)
VALUES ('20000000-0000-0000-0000-000000000006', 'SCHEMA_TEST', '{}');
BEGIN;
SELECT event_id FROM notification_outbox
WHERE sent_at IS NULL AND next_attempt_at <= CURRENT_TIMESTAMP
ORDER BY created_at, event_id LIMIT 1 FOR UPDATE SKIP LOCKED;
UPDATE notification_outbox SET attempts = attempts + 1, sent_at = CURRENT_TIMESTAMP;
ROLLBACK;
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM notification_outbox
        WHERE attempts = 0 AND sent_at IS NULL AND created_at IS NOT NULL AND next_attempt_at IS NOT NULL)
        OR NOT EXISTS (SELECT 1 FROM pg_indexes
            WHERE schemaname=current_schema() AND indexname='notification_outbox_pending_idx') THEN
        RAISE EXCEPTION 'Outbox schema, defaults, index or rollback failed';
    END IF;
END $$;
INSERT INTO attendance_schedule_batches(id,schedule_revision,actor_user_id,request_body,replaced_rules,occurred_at)
VALUES ('20000000-0000-0000-0000-000000000001',1,'SCHEMA_TEST','{}','[]',CURRENT_TIMESTAMP);
INSERT INTO work_schedule_rules(id,employee_id,batch_id,shift_id,shift_version,weekday,effective_from,effective_until)
SELECT '20000000-0000-0000-0000-000000000002',NULL,'20000000-0000-0000-0000-000000000001',id,version,1,'2030-01-07','9999-12-31' FROM work_shifts;
INSERT INTO attendance_daily(id,employee_id,work_date,shift_id,shift_version,employee_snapshot,leave_snapshot,holiday_snapshot,source_observed_at,check_in,check_out)
SELECT '20000000-0000-0000-0000-000000000003','10000000-0000-0000-0000-000000000001','2030-01-07',id,version,
'{}','[]','[]',CURRENT_TIMESTAMP,'2030-01-07 08:00+07','2030-01-07 17:30+07' FROM work_shifts;
INSERT INTO attendance_events(id,day_id,event_type,event_at,method,actor_user_id,source_ip)
VALUES ('20000000-0000-0000-0000-000000000004','20000000-0000-0000-0000-000000000003','CHECK_IN','2030-01-07 08:00+07','CORPORATE_NETWORK','SCHEMA_TEST','127.0.0.1');
DO $$
DECLARE blocked boolean := false;
BEGIN
    IF (SELECT count(*) FROM attendance_daily) <> 1 OR (SELECT count(*) FROM work_schedule_rules) <> 1
        OR (SELECT count(*) FROM attendance_events) <> 1 THEN
        RAISE EXCEPTION 'Incorrect fixture record counts';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=current_schema()
        AND ((table_name IN ('attendance_daily','attendance_requests') AND column_name='shift_definition')
          OR (table_name='work_schedule_rules' AND column_name='definition'))) THEN
        RAISE EXCEPTION 'Duplicate definition columns still exist';
    END IF;
    BEGIN
        UPDATE attendance_daily SET shift_version=999;
    EXCEPTION WHEN foreign_key_violation THEN blocked := true;
    END;
    IF NOT blocked THEN RAISE EXCEPTION 'Missing shift revision was accepted'; END IF;
    blocked := false;
    BEGIN
        UPDATE shift_revisions SET definition='{}';
    EXCEPTION WHEN raise_exception THEN blocked := true;
    END;
    IF NOT blocked THEN RAISE EXCEPTION 'Revision update was accepted'; END IF;
    blocked := false;
    BEGIN
        DELETE FROM shift_revisions;
    EXCEPTION WHEN foreign_key_violation THEN blocked := true;
    END;
    IF NOT blocked THEN RAISE EXCEPTION 'Referenced revision deletion was accepted'; END IF;
    blocked := false;
    BEGIN
        INSERT INTO attendance_events SELECT '20000000-0000-0000-0000-000000000005',day_id,event_type,event_at,method,actor_user_id,source_ip FROM attendance_events;
    EXCEPTION WHEN unique_violation THEN blocked := true;
    END;
    IF NOT blocked THEN RAISE EXCEPTION 'Duplicate punch event was accepted'; END IF;
END $$;
SQL
echo "PostgreSQL schema checks passed (isolated schema). Log: $log_file"
