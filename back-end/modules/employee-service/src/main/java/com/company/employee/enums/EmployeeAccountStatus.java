package com.company.employee.enums;

/**
 * Enum representing the status of an account.
 */
public enum EmployeeAccountStatus {
    NOT_CREATED, // The account has not been created yet.
    PENDING_ACTIVATION, // The account is created but pending activation.
    ACTIVE, // The account is active and can be used.
    SUSPENDED, // The account is temporarily suspended.
    DISABLED // The account is disabled.
}
