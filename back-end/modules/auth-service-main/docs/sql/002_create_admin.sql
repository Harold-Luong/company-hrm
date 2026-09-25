-- Active: 1790151670461@@127.0.0.1@5432@auth_db@public
-- LOCAL DEVELOPMENT ONLY. Run in auth_db as auth_user after 001.
-- Login: admin@company.com / Admin@123456 (BCrypt cost 12).
-- Links to EMP005 in Employee's seed; login email need not equal contact email.
-- Run Employee's seed first when testing the full HRM flow.
-- Repeat runs preserve existing password, active flag and roles.

BEGIN;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM users
        WHERE (email = 'admin@company.com'
            OR employee_id = '10000000-0000-0000-0000-000000000005'::UUID)
          AND (email <> 'admin@company.com'
            OR employee_id <> '10000000-0000-0000-0000-000000000005'::UUID)
    ) THEN
        RAISE EXCEPTION 'Admin seed conflicts with an existing email/employee mapping; no account was changed';
    END IF;
END $$;

WITH inserted AS (
    INSERT INTO users (employee_id, email, password_hash, is_active)
    VALUES (
        '10000000-0000-0000-0000-000000000005',
        'admin@company.com',
        '$2a$12$.7Frn6Kx70AP8HNU1BvPz.iP617VcpK.K0iZ35t1.OWcT3by7HSZG',
        TRUE
    )
    ON CONFLICT (email) DO NOTHING
    RETURNING id
)
INSERT INTO user_roles (user_id, role)
SELECT inserted.id, roles.role
FROM inserted CROSS JOIN (VALUES ('EMPLOYEE'), ('ADMIN')) AS roles(role);

COMMIT;
