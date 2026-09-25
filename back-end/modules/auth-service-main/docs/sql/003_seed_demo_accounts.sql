-- LOCAL DEVELOPMENT ONLY. Run in auth_db as auth_user after 001 and 002.
-- All demo accounts use Admin@123456 (BCrypt cost 12).
-- employee_id values match employee-service/src/main/resources/static/sql/seed_employee_data.sql.
-- EMP004 intentionally has no account; EMP005 is the Admin created by 002.
-- Repeat runs preserve existing password, active flag and roles.

BEGIN;

CREATE TEMP TABLE auth_demo_seed (
    employee_id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    is_active BOOLEAN NOT NULL,
    roles TEXT[] NOT NULL
) ON COMMIT DROP;

INSERT INTO auth_demo_seed (employee_id, email, is_active, roles)
VALUES
    ('10000000-0000-0000-0000-000000000001', 'hr@company.com', TRUE, ARRAY['EMPLOYEE', 'HR']),
    ('10000000-0000-0000-0000-000000000002', 'manager@company.com', TRUE, ARRAY['EMPLOYEE', 'MANAGER']),
    ('10000000-0000-0000-0000-000000000003', 'employee@company.com', TRUE, ARRAY['EMPLOYEE']),
    ('10000000-0000-0000-0000-000000000006', 'disabled@company.com', FALSE, ARRAY['EMPLOYEE']);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM auth_demo_seed seed
        JOIN users account ON account.email = seed.email OR account.employee_id = seed.employee_id
        WHERE account.email <> seed.email OR account.employee_id <> seed.employee_id
    ) THEN
        RAISE EXCEPTION 'Demo seed conflicts with an existing email/employee mapping; no accounts were changed';
    END IF;
END $$;

WITH inserted AS (
    INSERT INTO users (employee_id, email, password_hash, is_active)
    SELECT employee_id, email,
        '$2a$12$.7Frn6Kx70AP8HNU1BvPz.iP617VcpK.K0iZ35t1.OWcT3by7HSZG', is_active
    FROM auth_demo_seed
    ON CONFLICT (email) DO NOTHING
    RETURNING id, email
)
INSERT INTO user_roles (user_id, role)
SELECT inserted.id, role.name
FROM inserted
JOIN auth_demo_seed seed ON seed.email = inserted.email
CROSS JOIN LATERAL unnest(seed.roles) AS role(name);

COMMIT;
