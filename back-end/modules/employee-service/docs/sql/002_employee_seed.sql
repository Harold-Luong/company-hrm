-- LOCAL DEVELOPMENT ONLY. Run after 001_employee_schema.sql as employee_user.
-- 4 departments, 5 positions and 6 employees; UUIDs match Auth's demo seed.
-- EMP004 has no seeded Auth account and is available for the Kafka/email flow.
-- New employees use NOT_CREATED: direct SQL seeds do not synchronize with Auth.
-- Replays preserve existing profiles, employment/account statuses and event versions.
-- Conflicting IDs/codes/emails abort the transaction; no requests or events are seeded.

BEGIN;

CREATE TEMP TABLE employee_demo_departments (
    id UUID PRIMARY KEY, code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL, description TEXT
) ON COMMIT DROP;
CREATE TEMP TABLE employee_demo_positions (
    id UUID PRIMARY KEY, code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL, description TEXT
) ON COMMIT DROP;
CREATE TEMP TABLE employee_demo_employees (
    id UUID PRIMARY KEY, employee_code VARCHAR(50) NOT NULL UNIQUE,
    first_name VARCHAR(100) NOT NULL, last_name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE, phone VARCHAR(30),
    date_of_birth DATE, hire_date DATE NOT NULL, status VARCHAR(30) NOT NULL,
    department_id UUID, position_id UUID, manager_id UUID
) ON COMMIT DROP;

INSERT INTO employee_demo_departments (id, code, name, description)
VALUES
    ('11111111-1111-1111-1111-111111111111', 'HR', 'Human Resources', 'Human Resources Department'),
    ('22222222-2222-2222-2222-222222222222', 'IT', 'Information Technology', 'Information Technology Department'),
    ('33333333-3333-3333-3333-333333333333', 'FIN', 'Finance', 'Finance Department'),
    ('44444444-4444-4444-4444-444444444444', 'SALES', 'Sales', 'Sales Department');

INSERT INTO employee_demo_positions (id, code, name, description)
VALUES
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', 'HR_MANAGER', 'HR Manager', 'Manages Human Resources'),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2', 'SOFTWARE_ENGINEER', 'Software Engineer', 'Develops and maintains software'),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa3', 'TEAM_LEAD', 'Team Lead', 'Leads a technical team'),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa4', 'ACCOUNTANT', 'Accountant', 'Handles accounting operations'),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa5', 'SALES_EXECUTIVE', 'Sales Executive', 'Handles sales activities');

INSERT INTO employee_demo_employees (id, employee_code, first_name, last_name, email, phone, date_of_birth, hire_date, status, department_id, position_id, manager_id)
VALUES
    ('10000000-0000-0000-0000-000000000001', 'EMP001', 'An', 'Nguyen', 'an.nguyen@company.com', '0901000001', '1990-04-15', '2020-01-10', 'ACTIVE', '11111111-1111-1111-1111-111111111111', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', NULL),
    ('10000000-0000-0000-0000-000000000002', 'EMP002', 'Binh', 'Tran', 'binh.tran@company.com', '0901000002', '1989-08-21', '2019-06-03', 'ACTIVE', '22222222-2222-2222-2222-222222222222', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa3', NULL),
    ('10000000-0000-0000-0000-000000000003', 'EMP003', 'Chi', 'Le', 'chi.le@company.com', '0901000003', '1998-02-12', '2024-07-01', 'ACTIVE', '22222222-2222-2222-2222-222222222222', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2', '10000000-0000-0000-0000-000000000002'),
    ('10000000-0000-0000-0000-000000000004', 'EMP004', 'Dung', 'Pham', 'dung.pham@company.com', '0901000004', '2000-11-05', '2026-09-01', 'PROBATION', '22222222-2222-2222-2222-222222222222', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2', '10000000-0000-0000-0000-000000000002'),
    ('10000000-0000-0000-0000-000000000005', 'EMP005', 'Ha', 'Vo', 'ha.vo@company.com', '0901000005', '1995-06-30', '2022-03-14', 'ACTIVE', '33333333-3333-3333-3333-333333333333', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa4', NULL),
    ('10000000-0000-0000-0000-000000000006', 'EMP006', 'Khanh', 'Do', 'khanh.do@company.com', '0901000006', '1997-09-18', '2023-05-22', 'ACTIVE', '44444444-4444-4444-4444-444444444444', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa5', NULL);

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
            OR existing.employee_code = seed.employee_code OR existing.email = seed.email
        WHERE existing.id <> seed.id OR existing.employee_code <> seed.employee_code
            OR existing.email <> seed.email
    ) THEN
        RAISE EXCEPTION 'Demo seed conflicts with an existing ID/code/email mapping; no data was changed';
    END IF;
END $$;

INSERT INTO departments (id, code, name, description)
SELECT id, code, name, description FROM employee_demo_departments
ON CONFLICT (id) DO NOTHING;

INSERT INTO positions (id, code, name, description)
SELECT id, code, name, description FROM employee_demo_positions
ON CONFLICT (id) DO NOTHING;

-- Managers and reports are inserted in one statement; the FK is checked after the statement.
INSERT INTO employees (id, employee_code, first_name, last_name, email, phone,
                       date_of_birth, hire_date, status, department_id, position_id, manager_id)
SELECT id, employee_code, first_name, last_name, email, phone,
       date_of_birth, hire_date, status, department_id, position_id, manager_id
FROM employee_demo_employees
ON CONFLICT (id) DO NOTHING;

COMMIT;
