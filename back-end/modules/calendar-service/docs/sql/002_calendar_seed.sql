-- Calendar data for 2026–2027. Run after 001_calendar_schema.sql.
-- Seed events are published. User-created or edited drafts are left unchanged.
BEGIN;

WITH seed(title, description, type, holiday_kind, start_date, end_date) AS (
    VALUES
        ('New Year''s Day — Ngày lễ theo lịch nguồn', NULL, 'HOLIDAY', 'PUBLIC_HOLIDAY', DATE '2026-01-01', DATE '2026-01-01'),
        ('New Year''s Day — Ghi chú trừ phép', 'trừ vào ngày phép', 'OTHER', NULL, DATE '2026-01-02', DATE '2026-01-02'),
        ('Lunar New Year (Tet Holiday) — Công ty cho nghỉ', '3 ngày nghỉ cty cho thêm', 'HOLIDAY', 'COMPANY_DAY_OFF', DATE '2026-02-13', DATE '2026-02-13'),
        ('Lunar New Year (Tet Holiday) — Khoảng chưa phân loại', NULL, 'OTHER', NULL, DATE '2026-02-14', DATE '2026-02-15'),
        ('Lunar New Year (Tet Holiday) — Ngày lễ theo lịch nguồn', '5 ngày nghỉ Tết theo quy định', 'HOLIDAY', 'PUBLIC_HOLIDAY', DATE '2026-02-16', DATE '2026-02-20'),
        ('Lunar New Year (Tet Holiday) — Khoảng chưa phân loại', NULL, 'OTHER', NULL, DATE '2026-02-21', DATE '2026-02-22'),
        ('Lunar New Year (Tet Holiday) — Công ty cho nghỉ', '3 ngày nghỉ cty cho thêm', 'HOLIDAY', 'COMPANY_DAY_OFF', DATE '2026-02-23', DATE '2026-02-24'),
        ('Hung King Festival', 'nghỉ bù cho ngày 26/04', 'HOLIDAY', 'SUBSTITUTE_DAY_OFF', DATE '2026-04-27', DATE '2026-04-27'),
        ('Liberation Day & May Day', NULL, 'HOLIDAY', 'PUBLIC_HOLIDAY', DATE '2026-04-30', DATE '2026-05-01'),
        ('Independence Day', NULL, 'HOLIDAY', 'PUBLIC_HOLIDAY', DATE '2026-09-02', DATE '2026-09-03'),
        ('Sports Day', NULL, 'HOLIDAY', 'COMPANY_DAY_OFF', DATE '2026-10-12', DATE '2026-10-12'),
        ('Culture Day', NULL, 'HOLIDAY', 'COMPANY_DAY_OFF', DATE '2026-11-03', DATE '2026-11-03'),
        ('Labor Thanksgiving Day - Substitute Holiday', NULL, 'HOLIDAY', 'COMPANY_DAY_OFF', DATE '2026-11-23', DATE '2026-11-23'),
        ('New Year''s Day', NULL, 'HOLIDAY', 'PUBLIC_HOLIDAY', DATE '2027-01-01', DATE '2027-01-01'),
        ('Lunar New Year (Tet Holiday) — Công ty cho nghỉ', '3 ngày nghỉ cty cho thêm', 'HOLIDAY', 'COMPANY_DAY_OFF', DATE '2027-02-03', DATE '2027-02-03'),
        ('Lunar New Year (Tet Holiday) — Ngày lễ theo lịch nguồn', '5 ngày nghỉ Tết theo quy định', 'HOLIDAY', 'PUBLIC_HOLIDAY', DATE '2027-02-04', DATE '2027-02-10'),
        ('Lunar New Year (Tet Holiday) — Công ty cho nghỉ', '3 ngày nghỉ cty cho thêm', 'HOLIDAY', 'COMPANY_DAY_OFF', DATE '2027-02-11', DATE '2027-02-12'),
        ('Lunar New Year (Tet Holiday) — Khoảng chưa phân loại', NULL, 'OTHER', NULL, DATE '2027-02-13', DATE '2027-02-14'),
        ('Hung King Festival', NULL, 'HOLIDAY', 'PUBLIC_HOLIDAY', DATE '2027-04-16', DATE '2027-04-16'),
        ('Liberation Day & May Day — Ngày lễ theo lịch nguồn', NULL, 'HOLIDAY', 'PUBLIC_HOLIDAY', DATE '2027-04-30', DATE '2027-05-01'),
        ('Liberation Day & May Day — Khoảng chưa phân loại', NULL, 'OTHER', NULL, DATE '2027-05-02', DATE '2027-05-02'),
        ('Liberation Day & May Day — Nghỉ bù', 'nghỉ bù cho ngày 01/05', 'HOLIDAY', 'SUBSTITUTE_DAY_OFF', DATE '2027-05-03', DATE '2027-05-03'),
        ('Independence Day', NULL, 'HOLIDAY', 'PUBLIC_HOLIDAY', DATE '2027-09-02', DATE '2027-09-03'),
        ('Vietnam Culture Day', NULL, 'COMPANY_EVENT', NULL, DATE '2027-11-24', DATE '2027-11-24'),
        ('Sports Day', NULL, 'HOLIDAY', 'COMPANY_DAY_OFF', DATE '2027-10-11', DATE '2027-10-11'),
        ('Labor Thanksgiving Day - Substitute Holiday', NULL, 'HOLIDAY', 'COMPANY_DAY_OFF', DATE '2027-11-23', DATE '2027-11-23')
), inserted AS (
    INSERT INTO calendar_events
        (title, description, type, holiday_kind, all_day, start_date, end_date,
         timezone, status, audience_type, created_by, updated_by)
    SELECT seed.title, seed.description, seed.type, seed.holiday_kind, true,
           seed.start_date, seed.end_date, 'Asia/Ho_Chi_Minh', 'PUBLISHED', 'ALL',
           'seed:everrise-vn', 'seed:everrise-vn'
    FROM seed
    WHERE NOT EXISTS (
        SELECT 1 FROM calendar_events existing
        WHERE existing.title = seed.title
          AND existing.type = seed.type
          AND existing.holiday_kind IS NOT DISTINCT FROM seed.holiday_kind
          AND existing.all_day
          AND existing.start_date = seed.start_date
          AND existing.end_date = seed.end_date
    )
    RETURNING *
), pending AS (
    SELECT existing.id, to_jsonb(existing) AS before_data
    FROM calendar_events existing
    JOIN seed ON existing.title = seed.title
        AND existing.type = seed.type
        AND existing.holiday_kind IS NOT DISTINCT FROM seed.holiday_kind
        AND existing.start_date = seed.start_date
        AND existing.end_date = seed.end_date
        AND existing.description IS NOT DISTINCT FROM seed.description
    WHERE existing.created_by = 'seed:everrise-vn'
      AND existing.updated_by = 'seed:everrise-vn'
      AND existing.version = 0
      AND existing.status = 'DRAFT'
      AND existing.audience_type = 'ALL'
      AND existing.all_day
      AND existing.location IS NULL
    FOR UPDATE OF existing
), published AS (
    UPDATE calendar_events event
    SET status = 'PUBLISHED', version = event.version + 1,
        updated_by = 'seed:everrise-vn', updated_at = CURRENT_TIMESTAMP
    FROM pending
    WHERE event.id = pending.id
    RETURNING event.*, pending.before_data
)
INSERT INTO calendar_event_audit
    (calendar_event_id, action, actor_user_id, reason, before_data, after_data)
SELECT id, 'CREATE', created_by, 'Seed calendar data', NULL, to_jsonb(inserted)
FROM inserted
UNION ALL
SELECT id, 'PUBLISH', updated_by, 'Publish seeded calendar data',
       before_data, to_jsonb(published) - 'before_data'
FROM published;

COMMIT;
