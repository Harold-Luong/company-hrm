package com.company.notification.exception;

/** Service-owned exception messages. Preserve API text compatibility when editing. */
public final class ExceptionMessages {
    private ExceptionMessages() {
    }

    // AccountResults
    public static final String INVALID_ACCOUNT_RESULT = "Invalid account result";
    public static final String INVALID_ACCOUNT_RESULT_DATA = "Invalid account result data";

    // Notifications
    public static final String INVALID_NOTIFICATION_EVENT = "Invalid notification event";

    // AccountResultConsumer
    public static final String ACCOUNT_RESULT_KEY_MISMATCH = "Account result key does not match employeeId";

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
}
