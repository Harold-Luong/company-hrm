-- Run on a disposable database after the schema and unmodified seed.
-- Run the entire script in a SQL editor or psql; test mutations are rolled back.
BEGIN;

DO $checks$
DECLARE
    sample_id BIGINT;
    audit_count BIGINT;
    timed_id BIGINT;
BEGIN
    IF (SELECT count(*) FROM calendar_events WHERE created_by = 'seed:everrise-vn') <> 26
        OR (SELECT count(*) FROM calendar_events WHERE created_by = 'seed:everrise-vn' AND type = 'HOLIDAY') <> 20
        OR (SELECT count(*) FROM calendar_events WHERE created_by = 'seed:everrise-vn' AND type = 'OTHER') <> 5
        OR (SELECT count(*) FROM calendar_events WHERE created_by = 'seed:everrise-vn' AND type = 'COMPANY_EVENT') <> 1
        OR EXISTS (SELECT 1 FROM calendar_events WHERE created_by = 'seed:everrise-vn'
            AND (status <> 'PUBLISHED' OR audience_type <> 'ALL' OR NOT all_day)) THEN
        RAISE EXCEPTION 'Unexpected seed size, classification or initial state';
    END IF;

    IF EXISTS (
        SELECT 1 FROM calendar_events e
        WHERE e.created_by = 'seed:everrise-vn'
          AND (SELECT count(*) FROM calendar_event_audit a
               WHERE a.calendar_event_id = e.id AND a.action = 'CREATE') <> 1
    ) THEN
        RAISE EXCEPTION 'Every seeded event must have exactly one CREATE audit';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM calendar_events WHERE created_by = 'seed:everrise-vn'
        AND start_date = DATE '2026-01-02' AND end_date = DATE '2026-01-02'
        AND type = 'OTHER' AND holiday_kind IS NULL AND description = 'trừ vào ngày phép') THEN
        RAISE EXCEPTION 'Paid-leave note must remain OTHER';
    END IF;

    IF (SELECT count(*) FROM calendar_events WHERE created_by = 'seed:everrise-vn'
        AND title IN ('Sports Day', 'Culture Day', 'Labor Thanksgiving Day - Substitute Holiday')
        AND type = 'HOLIDAY' AND holiday_kind = 'COMPANY_DAY_OFF') <> 5 THEN
        RAISE EXCEPTION 'Expected five company days off for holidays originating in Japan';
    END IF;

    SELECT id INTO STRICT sample_id FROM calendar_events
        WHERE created_by = 'seed:everrise-vn' AND title = 'Liberation Day & May Day'
          AND start_date = DATE '2026-04-30' AND end_date = DATE '2026-05-01';
    -- A May query must include a Holiday starting in April.
    IF NOT EXISTS (SELECT 1 FROM calendar_events WHERE id = sample_id
        AND start_date <= DATE '2026-05-31' AND end_date >= DATE '2026-05-01') THEN
        RAISE EXCEPTION 'Range overlap excludes a cross-month Holiday';
    END IF;

    BEGIN
        UPDATE calendar_events SET end_date = start_date - 1 WHERE id = sample_id;
        RAISE EXCEPTION 'Expected reversed date range to be rejected';
    EXCEPTION WHEN check_violation THEN NULL; END;
    BEGIN
        UPDATE calendar_events SET end_date = NULL WHERE id = sample_id;
        RAISE EXCEPTION 'Expected null date boundary to be rejected';
    EXCEPTION WHEN check_violation THEN NULL; END;
    BEGIN
        UPDATE calendar_events SET start_at = CURRENT_TIMESTAMP WHERE id = sample_id;
        RAISE EXCEPTION 'Expected mixed date/time fields to be rejected';
    EXCEPTION WHEN check_violation THEN NULL; END;
    BEGIN
        UPDATE calendar_events SET holiday_kind = NULL WHERE id = sample_id;
        RAISE EXCEPTION 'Expected Holiday without kind to be rejected';
    EXCEPTION WHEN check_violation THEN NULL; END;
    BEGIN
        UPDATE calendar_events SET type = 'OTHER' WHERE id = sample_id;
        RAISE EXCEPTION 'Expected non-Holiday with holiday_kind to be rejected';
    EXCEPTION WHEN check_violation THEN NULL; END;
    BEGIN
        UPDATE calendar_events SET audience_type = 'DEPARTMENT' WHERE id = sample_id;
        RAISE EXCEPTION 'Expected unsupported audience to be rejected';
    EXCEPTION WHEN check_violation THEN NULL; END;
    BEGIN
        UPDATE calendar_events SET status = 'COMPLETED' WHERE id = sample_id;
        RAISE EXCEPTION 'Expected unsupported persisted status to be rejected';
    EXCEPTION WHEN check_violation THEN NULL; END;
    BEGIN
        UPDATE calendar_events SET timezone = 'UTC' WHERE id = sample_id;
        RAISE EXCEPTION 'Expected unsupported business timezone to be rejected';
    EXCEPTION WHEN check_violation THEN NULL; END;

    INSERT INTO calendar_events (title, type, all_day, start_at, end_at, created_by, updated_by)
    VALUES ('Timed event verification', 'MEETING', false,
        '2027-01-15T23:00:00+07:00', '2027-01-16T01:00:00+07:00', 'test', 'test')
    RETURNING id INTO timed_id;
    IF NOT EXISTS (SELECT 1 FROM calendar_events WHERE id = timed_id
        AND start_at = TIMESTAMPTZ '2027-01-15T16:00:00Z'
        AND end_at = TIMESTAMPTZ '2027-01-15T18:00:00Z') THEN
        RAISE EXCEPTION 'Offset timestamps must represent the same UTC instants';
    END IF;
    BEGIN
        UPDATE calendar_events SET end_at = start_at WHERE id = timed_id;
        RAISE EXCEPTION 'Expected empty timed range to be rejected';
    EXCEPTION WHEN check_violation THEN NULL; END;
    BEGIN
        UPDATE calendar_events SET type = 'HOLIDAY', holiday_kind = 'PUBLIC_HOLIDAY' WHERE id = timed_id;
        RAISE EXCEPTION 'Expected timed Holiday to be rejected';
    EXCEPTION WHEN check_violation THEN NULL; END;

    SELECT count(*) INTO audit_count FROM calendar_event_audit WHERE calendar_event_id = sample_id;
    DELETE FROM calendar_events WHERE id = sample_id;
    IF audit_count <> (SELECT count(*) FROM calendar_event_audit WHERE calendar_event_id = sample_id) THEN
        RAISE EXCEPTION 'Deletion must retain audit history';
    END IF;
END
$checks$;

ROLLBACK;
