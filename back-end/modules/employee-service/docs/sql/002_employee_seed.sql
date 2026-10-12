-- LOCAL DEVELOPMENT ONLY. Run after 001_employee_schema.sql as employee_user.
-- 4 departments, 5 positions and 7 employees; existing UUIDs match Auth's demo seed.
-- EMP004 has no seeded Auth account and is available for the Kafka/email flow.
-- All seven fictional profiles include gender, address and emergency contact details.
-- New employees use NOT_CREATED: direct SQL seeds do not synchronize with Auth.
-- Replays fill missing contact details and replace untouched legacy gender placeholders.
-- Existing populated details, employment/account statuses and event versions are preserved.
-- Conflicting IDs/codes/emails abort the transaction; no requests or events are seeded.

BEGIN;

CREATE TEMP TABLE employee_demo_departments (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    description TEXT
) ON
COMMIT
DROP;

CREATE TEMP TABLE employee_demo_positions (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    description TEXT
) ON
COMMIT
DROP;

CREATE TEMP TABLE employee_demo_employees (
    id UUID PRIMARY KEY,
    employee_code VARCHAR(50) NOT NULL UNIQUE,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    personal_email VARCHAR(255) NOT NULL,
    phone VARCHAR(30) NOT NULL,
    date_of_birth DATE NOT NULL,
    gender VARCHAR(20) NOT NULL,
    address VARCHAR(255) NOT NULL,
    contact_relative VARCHAR(255) NOT NULL,
    contact_relative_phone VARCHAR(30) NOT NULL,
    hire_date DATE NOT NULL,
    status VARCHAR(30) NOT NULL,
    department_id UUID NOT NULL,
    position_id UUID NOT NULL,
    manager_id UUID
) ON
COMMIT
DROP;

INSERT INTO
    employee_demo_departments (id, code, name, description)
VALUES (
        '11111111-1111-1111-1111-111111111111',
        'HR',
        'Human Resources',
        'Human Resources Department'
    ),
    (
        '22222222-2222-2222-2222-222222222222',
        'IT',
        'Information Technology',
        'Information Technology Department'
    ),
    (
        '33333333-3333-3333-3333-333333333333',
        'FIN',
        'Finance',
        'Finance Department'
    ),
    (
        '44444444-4444-4444-4444-444444444444',
        'SALES',
        'Sales',
        'Sales Department'
    );

INSERT INTO
    employee_demo_positions (id, code, name, description)
VALUES (
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1',
        'HR_MANAGER',
        'HR Manager',
        'Manages Human Resources'
    ),
    (
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2',
        'SOFTWARE_ENGINEER',
        'Software Engineer',
        'Develops and maintains software'
    ),
    (
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa3',
        'TEAM_LEAD',
        'Team Lead',
        'Leads a technical team'
    ),
    (
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa4',
        'ACCOUNTANT',
        'Accountant',
        'Handles accounting operations'
    ),
    (
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa5',
        'SALES_EXECUTIVE',
        'Sales Executive',
        'Handles sales activities'
    );

INSERT INTO
    employee_demo_employees (
        id,
        employee_code,
        first_name,
        last_name,
        personal_email,
        phone,
        date_of_birth,
        gender,
        address,
        contact_relative,
        contact_relative_phone,
        hire_date,
        status,
        department_id,
        position_id,
        manager_id
    )
VALUES (
        '10000000-0000-0000-0000-000000000001',
        'EMP001',
        'An',
        'Nguyen',
        'an.nguyen@company.com',
        '0901000001',
        '1990-04-15',
        'FEMALE',
        '101 Đường Demo, TP. Hồ Chí Minh',
        'Nguyễn Văn Hùng (cha)',
        '0902000001',
        '2020-01-10',
        'ACTIVE',
        '11111111-1111-1111-1111-111111111111',
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1',
        NULL
    ),
    (
        '10000000-0000-0000-0000-000000000002',
        'EMP002',
        'Binh',
        'Tran',
        'binh.tran@company.com',
        '0901000002',
        '1989-08-21',
        'MALE',
        '102 Đường Demo, TP. Hồ Chí Minh',
        'Trần Thị Mai (mẹ)',
        '0902000002',
        '2019-06-03',
        'ACTIVE',
        '22222222-2222-2222-2222-222222222222',
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa3',
        NULL
    ),
    (
        '10000000-0000-0000-0000-000000000003',
        'EMP003',
        'Chi',
        'Le',
        'chi.le@company.com',
        '0901000003',
        '1998-02-12',
        'FEMALE',
        '103 Đường Demo, TP. Hồ Chí Minh',
        'Lê Văn Nam (cha)',
        '0902000003',
        '2024-07-01',
        'ACTIVE',
        '22222222-2222-2222-2222-222222222222',
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2',
        '10000000-0000-0000-0000-000000000002'
    ),
    (
        '10000000-0000-0000-0000-000000000004',
        'EMP004',
        'Dung',
        'Pham',
        'dung.pham@company.com',
        '0901000004',
        '2000-11-05',
        'MALE',
        '104 Đường Demo, TP. Hồ Chí Minh',
        'Phạm Thị Lan (mẹ)',
        '0902000004',
        '2026-09-01',
        'PROBATION',
        '22222222-2222-2222-2222-222222222222',
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2',
        '10000000-0000-0000-0000-000000000002'
    ),
    (
        '10000000-0000-0000-0000-000000000005',
        'EMP005',
        'Ha',
        'Vo',
        'ha.vo@company.com',
        '0901000005',
        '1995-06-30',
        'FEMALE',
        '105 Đường Demo, TP. Hồ Chí Minh',
        'Võ Văn Sơn (cha)',
        '0902000005',
        '2022-03-14',
        'ACTIVE',
        '33333333-3333-3333-3333-333333333333',
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa4',
        NULL
    ),
    (
        '10000000-0000-0000-0000-000000000006',
        'EMP006',
        'Khanh',
        'Do',
        'khanh.do@company.com',
        '0901000006',
        '1997-09-18',
        'MALE',
        '106 Đường Demo, TP. Hồ Chí Minh',
        'Đỗ Thị Hoa (mẹ)',
        '0902000006',
        '2023-05-22',
        'ACTIVE',
        '44444444-4444-4444-4444-444444444444',
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa5',
        NULL
    );

-- Additional complete profile, without a seeded Auth account.
INSERT INTO employee_demo_employees (
    id, employee_code, first_name, last_name, personal_email,
    phone, date_of_birth, gender, address, contact_relative, contact_relative_phone,
    hire_date, status, department_id, position_id, manager_id
)
VALUES (
    '10000000-0000-0000-0000-000000000007',
    'EMP007',
    'Minh Anh',
    'Nguyễn',
    'minhanh.nguyen@example.com',
    '0901000007',
    DATE '1999-05-20',
    'FEMALE',
    '123 Đường Demo, TP. Hồ Chí Minh',
    'Nguyễn Văn Minh (cha)',
    '0902000007',
    DATE '2025-01-06',
    'ACTIVE',
    '22222222-2222-2222-2222-222222222222',
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2',
    '10000000-0000-0000-0000-000000000002'
);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM employee_demo_departments seed
        JOIN departments existing ON existing.id = seed.id OR existing.code = seed.code
        WHERE existing.id <> seed.id OR existing.code <> seed.code
    ) OR EXISTS (
        SELECT 1 FROM employee_demo_positions seed
        JOIN positions existing ON existing.id = seed.id OR existing.code = seed.code
        WHERE existing.id <> seed.id OR existing.code <> seed.code
    ) OR EXISTS (
        SELECT 1 FROM employee_demo_employees seed
        JOIN employees existing ON existing.id = seed.id
            OR existing.employee_code = seed.employee_code
        WHERE existing.id <> seed.id OR existing.employee_code <> seed.employee_code
            OR existing.personal_email <> seed.personal_email
    ) THEN
        RAISE EXCEPTION 'Demo seed conflicts with an existing ID/code/personal_email mapping; no data was changed';
    END IF;
END $$;

INSERT INTO
    departments (id, code, name, description)
SELECT id, code, name, description
FROM employee_demo_departments
ON CONFLICT (id) DO NOTHING;

INSERT INTO
    positions (id, code, name, description)
SELECT id, code, name, description
FROM employee_demo_positions
ON CONFLICT (id) DO NOTHING;

-- Managers and reports are inserted in one statement; the FK is checked after the statement.
INSERT INTO
    employees (
        id,
        employee_code,
        first_name,
        last_name,
        personal_email,
        phone,
        date_of_birth,
        gender,
        address,
        contact_relative,
        contact_relative_phone,
        hire_date,
        status,
        department_id,
        position_id,
        manager_id
    )
SELECT
    id,
    employee_code,
    first_name,
    last_name,
    personal_email,
    phone,
    date_of_birth,
    gender,
    address,
    contact_relative,
    contact_relative_phone,
    hire_date,
    status,
    department_id,
    position_id,
    manager_id
FROM employee_demo_employees
ON CONFLICT (id) DO NOTHING;

-- Upgrade the original partial demo profiles without overwriting user-entered values.
-- OTHER is a valid gender: only replace it on a legacy profile never edited since creation.
UPDATE employees existing
SET
    address = CASE WHEN NULLIF(btrim(existing.address), '') IS NULL
        THEN seed.address ELSE existing.address END,
    contact_relative = CASE WHEN NULLIF(btrim(existing.contact_relative), '') IS NULL
        THEN seed.contact_relative ELSE existing.contact_relative END,
    contact_relative_phone = CASE WHEN NULLIF(btrim(existing.contact_relative_phone), '') IS NULL
        THEN seed.contact_relative_phone ELSE existing.contact_relative_phone END,
    gender = CASE WHEN existing.gender = 'OTHER' AND existing.updated_at = existing.created_at
        THEN seed.gender ELSE existing.gender END,
    updated_at = CURRENT_TIMESTAMP
FROM employee_demo_employees seed
WHERE existing.id = seed.id
    AND (
        NULLIF(btrim(existing.address), '') IS NULL
        OR NULLIF(btrim(existing.contact_relative), '') IS NULL
        OR NULLIF(btrim(existing.contact_relative_phone), '') IS NULL
        OR (existing.gender = 'OTHER' AND existing.updated_at = existing.created_at)
    );

COMMIT;
