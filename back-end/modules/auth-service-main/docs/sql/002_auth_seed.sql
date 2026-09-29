-- LOCAL DEVELOPMENT ONLY. Run after 001_auth_schema.sql as auth_user.
-- Newly created accounts all use Admin@123456 (BCrypt cost 12).
-- Covers ADMIN, HR, MANAGER, EMPLOYEE and an inactive account.
-- IDs match Employee's seed; EMP004 stays unprovisioned for the Kafka/email flow.
-- Replays preserve existing passwords, roles, activation flags and versions.
-- Conflicting email/employee mappings abort the entire seed transaction.
-- No refresh sessions, activation tokens, email jobs or Kafka events are seeded.

BEGIN;

CREATE TEMP TABLE auth_demo_seed (
    employee_id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    is_active BOOLEAN NOT NULL,
    roles TEXT[] NOT NULL
) ON COMMIT DROP;

INSERT INTO auth_demo_seed (employee_id, email, is_active, roles)
VALUES
    ('10000000-0000-0000-0000-000000000005', 'admin@company.com', TRUE, ARRAY['EMPLOYEE', 'ADMIN']),
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
    INSERT INTO users (employee_id, email, password_hash, is_active, activation_pending)
    SELECT employee_id, email,
        '$2a$12$.7Frn6Kx70AP8HNU1BvPz.iP617VcpK.K0iZ35t1.OWcT3by7HSZG', is_active, FALSE
    FROM auth_demo_seed
    ON CONFLICT (email) DO NOTHING
    RETURNING id, email, employee_id
), versions AS (
    INSERT INTO account_link_versions (employee_id, version)
    SELECT employee_id, 1 FROM inserted
    ON CONFLICT (employee_id) DO NOTHING
)
INSERT INTO user_roles (user_id, role)
SELECT inserted.id, role.name
FROM inserted
JOIN auth_demo_seed seed ON seed.email = inserted.email
CROSS JOIN LATERAL unnest(seed.roles) AS role(name);

COMMIT;
