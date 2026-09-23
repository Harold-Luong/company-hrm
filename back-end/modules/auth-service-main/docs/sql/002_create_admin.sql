-- Replace sample employee UUIDs with the corresponding IDs from Employee.
INSERT INTO
    users (
        employee_id,
        email,
        password_hash,
        is_active
    )
VALUES (
        '550e8400-e29b-41d4-a716-000000000001',
        'hr@company.com',
        '$2a$12$RdwvI6YlZMi0cO81rnOdgOPt9IpMr/oW47LkrLmdTaBXooyiNente',
        true
    ),
    (
        '550e8400-e29b-41d4-a716-000000000002',
        'manager@company.com',
        '$2a$12$RdwvI6YlZMi0cO81rnOdgOPt9IpMr/oW47LkrLmdTaBXooyiNente',
        true
    ),
    (
        '550e8400-e29b-41d4-a716-000000000003',
        'employee@company.com',
        '$2a$12$RdwvI6YlZMi0cO81rnOdgOPt9IpMr/oW47LkrLmdTaBXooyiNente',
        true
    ),
    (
        '550e8400-e29b-41d4-a716-000000000004',
        'admin@company.com',
        '$2a$12$RdwvI6YlZMi0cO81rnOdgOPt9IpMr/oW47LkrLmdTaBXooyiNente',
        true
    );

INSERT INTO
    user_roles (user_id, role)
VALUES (1, 'EMPLOYEE'),
    (1, 'HR'),
    (2, 'EMPLOYEE'),
    (2, 'MANAGER'),
    (3, 'EMPLOYEE'),
    (4, 'EMPLOYEE'),
    (4, 'ADMIN');
