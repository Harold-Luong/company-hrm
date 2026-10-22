package com.company.employee.exception;

/** Service-owned exception messages. Preserve API text compatibility when editing. */
public final class ExceptionMessages {
    private ExceptionMessages() {
    }

    // AccountLifecycleService
    public static final String INVALID_ACCOUNT_LIFECYCLE_EVENT = "Invalid account lifecycle event";
    public static final String LIFECYCLE_EMPLOYEE_NOT_FOUND = "Lifecycle employee does not exist";

    // AccountRequestService
    public static final String PROVISIONING_DISABLED = "Account provisioning is disabled";
    public static final String INVALID_PROVISIONING_REQUEST =
            "Valid employeeId, Idempotency-Key and email are required";
    public static final String EMPLOYEE_NOT_FOUND = "Employee not found";
    public static final String IDEMPOTENCY_DATA_CONFLICT = "Idempotency-Key was used with different data";
    public static final String ACCOUNT_REQUEST_PENDING = "An account request is already pending";
    public static final String ACCOUNT_REQUEST_NOT_FOUND = "Account request not found";
    public static final String INVALID_ACCOUNT_RESULT_ENVELOPE = "Invalid account result envelope";
    public static final String INVALID_SUCCESSFUL_ACCOUNT_RESULT = "Invalid successful account result";
    public static final String INVALID_FAILED_ACCOUNT_RESULT = "Invalid failed account result";
    public static final String RESULT_EMPLOYEE_NOT_FOUND = "Result employee does not exist";
    public static final String ACCOUNT_RESULT_REQUEST_MISMATCH = "Result does not match its account request";
    public static final String EVENT_ID_REUSED = "Event ID reused across requests";
    public static final String ACCOUNT_RESULT_CONFLICT = "Conflicting terminal result for account request";

    // AccountResultConsumer
    public static final String ACCOUNT_RESULT_KEY_MISMATCH = "Account result key does not match employeeId";
    public static final String ACCOUNT_LIFECYCLE_KEY_MISMATCH = "Account lifecycle key does not match employeeId";

    // EmployeeService
    public static final String INVALID_BIRTH_DATE = "dateOfBirth must be before hireDate";
    public static final String MANAGER_HIERARCHY_CYCLE = "managerId must not create a cycle in the manager hierarchy";

    // Security
    public static final String JWT_PUBLIC_KEY_REQUIRED = "JWT access public key is required";
    public static final String JWT_PUBLIC_KEY_TOO_SHORT = "JWT access public key must be at least 2048 bits";
    public static final String SERVICE_UNAVAILABLE_TITLE = "Service Unavailable";
    public static final String SESSION_VERIFICATION_UNAVAILABLE = "Session verification is unavailable";
    public static final String UNAUTHORIZED_TITLE = "Unauthorized";
    public static final String ACCESS_TOKEN_REQUIRED = "A valid access token is required";
    public static final String FORBIDDEN_TITLE = "Forbidden";
    public static final String ACCESS_DENIED = "Access is denied";
    public static final String SESSION_REPLACED = "Session replaced or signed out";
    public static final String INVALID_ACCESS_TOKEN_CLAIMS = "Invalid access token claims";

    // DuplicateDepartmentException
    public static final String DUPLICATE_DEPARTMENT_CODE = "Department code already exists";

    // DuplicatePositionException
    public static final String DUPLICATE_POSITION_CODE = "Position code already exists";

    // GlobalExceptionHandler
    public static final String DATA_CONFLICT =
            "Data conflicts with an existing record or a referenced record has changed";
    public static final String REQUEST_VALIDATION_FAILED = "Request validation failed";

    // Parameterized messages
    public static final String RESOURCE_NOT_FOUND = "%s not found: %s";
    public static final String DUPLICATE_EMPLOYEE = "Employee with this %s already exists";
}
