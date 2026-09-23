
-- =========================================================
-- Departments
-- =========================================================
INSERT INTO
    departments (id, code, name, description)
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

-- =========================================================
-- Positions
-- =========================================================
INSERT INTO
    positions (id, code, name, description)
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

-- =========================================================
-- Employees
-- Insert managers first so manager_id can reference them.
-- =========================================================

-- HR Manager
INSERT INTO
    employees (
        id,
        employee_code,
        first_name,
        last_name,
        email,
        phone,
        date_of_birth,
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
        '2020-01-10',
        'ACTIVE',
        '11111111-1111-1111-1111-111111111111',
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1',
        NULL
    );

-- IT Team Lead
INSERT INTO
    employees (
        id,
        employee_code,
        first_name,
        last_name,
        email,
        phone,
        date_of_birth,
        hire_date,
        status,
        department_id,
        position_id,
        manager_id
    )
VALUES (
        '10000000-0000-0000-0000-000000000002',
        'EMP002',
        'Binh',
        'Tran',
        'binh.tran@company.com',
        '0901000002',
        '1989-08-21',
        '2019-06-03',
        'ACTIVE',
        '22222222-2222-2222-2222-222222222222',
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa3',
        NULL
    );

-- Software Engineer
INSERT INTO
    employees (
        id,
        employee_code,
        first_name,
        last_name,
        email,
        phone,
        date_of_birth,
        hire_date,
        status,
        department_id,
        position_id,
        manager_id
    )
VALUES (
        '10000000-0000-0000-0000-000000000003',
        'EMP003',
        'Chi',
        'Le',
        'chi.le@company.com',
        '0901000003',
        '1998-02-12',
        '2024-07-01',
        'ACTIVE',
        '22222222-2222-2222-2222-222222222222',
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2',
        '10000000-0000-0000-0000-000000000002'
    );

-- Software Engineer on probation
INSERT INTO
    employees (
        id,
        employee_code,
        first_name,
        last_name,
        email,
        phone,
        date_of_birth,
        hire_date,
        status,
        department_id,
        position_id,
        manager_id
    )
VALUES (
        '10000000-0000-0000-0000-000000000004',
        'EMP004',
        'Dung',
        'Pham',
        'dung.pham@company.com',
        '0901000004',
        '2000-11-05',
        '2026-09-01',
        'PROBATION',
        '22222222-2222-2222-2222-222222222222',
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2',
        '10000000-0000-0000-0000-000000000002'
    );

-- Accountant
INSERT INTO
    employees (
        id,
        employee_code,
        first_name,
        last_name,
        email,
        phone,
        date_of_birth,
        hire_date,
        status,
        department_id,
        position_id,
        manager_id
    )
VALUES (
        '10000000-0000-0000-0000-000000000005',
        'EMP005',
        'Ha',
        'Vo',
        'ha.vo@company.com',
        '0901000005',
        '1995-06-30',
        '2022-03-14',
        'ACTIVE',
        '33333333-3333-3333-3333-333333333333',
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa4',
        NULL
    );

-- Sales Executive
INSERT INTO
    employees (
        id,
        employee_code,
        first_name,
        last_name,
        email,
        phone,
        date_of_birth,
        hire_date,
        status,
        department_id,
        position_id,
        manager_id
    )
VALUES (
        '10000000-0000-0000-0000-000000000006',
        'EMP006',
        'Khanh',
        'Do',
        'khanh.do@company.com',
        '0901000006',
        '1997-09-18',
        '2023-05-22',
        'ACTIVE',
        '44444444-4444-4444-4444-444444444444',
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa5',
        NULL
    );