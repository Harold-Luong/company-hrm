-- Existing databases only. Fresh databases use the current 001_leave_schema.sql.
-- Stop the old application and run this before deploying the two-type API.
BEGIN;

LOCK TABLE leave_requests IN ACCESS EXCLUSIVE MODE;

-- The old labels do not tell us whether leave should be paid or unpaid.
-- Require explicit classification instead of changing balances or history silently.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM leave_requests WHERE leave_type NOT IN ('ANNUAL', 'UNPAID')) THEN
        RAISE EXCEPTION 'Legacy leave types require review before upgrading'
            USING HINT = 'Review SICK/OTHER requests with HR, preserve their reasons/history, and reconcile approved annual leave with leave_balances before rerunning this script.';
    END IF;
END;
$$;

ALTER TABLE leave_requests DROP CONSTRAINT leave_requests_leave_type_check;
ALTER TABLE leave_requests ADD CONSTRAINT leave_requests_leave_type_check
    CHECK (leave_type IN ('ANNUAL', 'UNPAID'));

COMMIT;
