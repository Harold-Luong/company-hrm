INSERT INTO
    users (
        employee_id,
        email,
        password_hash,
        is_active
    )
VALUES (
        1,
        'hr@company.com',
        '$2a$12$RdwvI6YlZMi0cO81rnOdgOPt9IpMr/oW47LkrLmdTaBXooyiNente',
        true
    ),
    (
        2,
        'manager@company.com',
        '$2a$12$RdwvI6YlZMi0cO81rnOdgOPt9IpMr/oW47LkrLmdTaBXooyiNente',
        true
    ),
    (
        3,
        'employee@company.com',
        '$2a$12$RdwvI6YlZMi0cO81rnOdgOPt9IpMr/oW47LkrLmdTaBXooyiNente',
        true
    ),
    (
        4,
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
