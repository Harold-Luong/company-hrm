
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
            account_status IN ('NOT_CREATED', 'PENDING_ACTIVATION', 'ACTIVE', 'DISABLED')
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

-- Account provisioning infrastructure (also available as incremental migration 004).
CREATE TABLE IF NOT EXISTS account_provisioning_requests (
    request_id UUID PRIMARY KEY,
    employee_id UUID NOT NULL REFERENCES employees(id),
    pending_employee_id UUID UNIQUE,
    email VARCHAR(255) NOT NULL,
    requested_by VARCHAR(255) NOT NULL,
    idempotency_key UUID NOT NULL,
    correlation_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED')),
    error_code VARCHAR(80),
    result_event_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT account_request_idempotency_key UNIQUE (requested_by, idempotency_key),
    CONSTRAINT account_request_pending CHECK (
        (status = 'PENDING' AND pending_employee_id IS NOT NULL AND pending_employee_id = employee_id)
        OR (status <> 'PENDING' AND pending_employee_id IS NULL)
    )
);
CREATE INDEX IF NOT EXISTS account_request_employee_idx ON account_provisioning_requests (employee_id);
CREATE TABLE IF NOT EXISTS provisioning_processed_events (
    event_id UUID PRIMARY KEY,
    request_id UUID NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS employee_account_versions (
    employee_id UUID PRIMARY KEY,
    version BIGINT NOT NULL CHECK (version >= 0)
);

CREATE TABLE IF NOT EXISTS event_outbox (
    event_id UUID PRIMARY KEY,
    topic VARCHAR(200) NOT NULL,
    message_key VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    claim_token UUID,
    lease_until TIMESTAMP WITH TIME ZONE,
    sent_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX IF NOT EXISTS event_outbox_pending_idx ON event_outbox (sent_at, next_attempt_at);
