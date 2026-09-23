
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE departments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid (),
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    description TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE positions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid (),
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    description TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE employees (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid (),
    employee_code VARCHAR(50) NOT NULL UNIQUE,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    account_status VARCHAR(30) NOT NULL DEFAULT 'NOT_CREATED'
        CONSTRAINT chk_employee_account_status CHECK (
            account_status IN ('UNKNOWN', 'NOT_CREATED', 'PENDING_ACTIVATION', 'ACTIVE', 'DISABLED')
        ),
    phone VARCHAR(30),
    date_of_birth DATE,
    hire_date DATE NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PROBATION' CHECK (
        status IN (
            'ACTIVE',
            'INACTIVE',
            'PROBATION',
            'RESIGNED',
            'TERMINATED'
        )
    ),
    department_id UUID,
    position_id UUID,
    manager_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_employee_department FOREIGN KEY (department_id) REFERENCES departments (id) ON DELETE SET NULL,
    CONSTRAINT fk_employee_position FOREIGN KEY (position_id) REFERENCES positions (id) ON DELETE SET NULL,
    CONSTRAINT fk_employee_manager FOREIGN KEY (manager_id) REFERENCES employees (id) ON DELETE SET NULL,
    CONSTRAINT chk_employee_not_own_manager CHECK (
        manager_id IS NULL
        OR manager_id <> id
    )
);

CREATE INDEX idx_employees_department_id ON employees (department_id);

CREATE INDEX idx_employees_position_id ON employees (position_id);

CREATE INDEX idx_employees_manager_id ON employees (manager_id);

CREATE INDEX idx_employees_status ON employees (status);

CREATE INDEX idx_employees_hire_date ON employees (hire_date);
