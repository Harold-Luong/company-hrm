-- Apply to existing databases before deploying automatic employee codes.
-- Safe to replay: never restart the sequence or change existing employee codes.
CREATE SEQUENCE IF NOT EXISTS employee_code_seq START WITH 1 INCREMENT BY 1;
