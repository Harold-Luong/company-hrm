package com.example.authservice.exception;

/**
 * Auth service exception messages and explicit validation messages.
 * Keep response text compatible with frontend error mappings when editing.
 */
public final class ExceptionMessages {
    private ExceptionMessages() {
    }

    // Authentication and sessions
    public static final String AUTHENTICATION_REQUIRED = "Authentication is required";
    public static final String ACCESS_DENIED = "Access is denied";
    public static final String INVALID_CREDENTIALS = "Invalid email or password";
    public static final String USER_INACTIVE = "User is inactive";
    public static final String USER_NOT_FOUND = "User not found";
    public static final String USER_NOT_AUTHENTICATED = "User is not authenticated";
    public static final String INVALID_AUTHENTICATED_USER_ID = "Invalid user ID in authentication";
    public static final String LOGIN_THROTTLED = "Too many login attempts. Please try again later.";
    public static final String INVALID_ACCESS_TOKEN = "Invalid or expired access token";
    public static final String ACCESS_TOKEN_EXPIRATION_REQUIRED = "Access token must expire";
    public static final String ACCOUNT_NO_LONGER_EXISTS = "Account no longer exists";
    public static final String SESSION_REPLACED = "Session replaced or signed out";
    public static final String SESSION_NOT_FOUND = "Session not found";
    public static final String REFRESH_SESSION_CREATION_FAILED = "Failed to create refresh session";
    public static final String REFRESH_TOKEN_REQUIRED = "Refresh token is required";
    public static final String REFRESH_COOKIE_REQUIRED = "Refresh cookie is required";
    public static final String INVALID_REFRESH_TOKEN = "Invalid refresh token";
    public static final String INVALID_REFRESH_TOKEN_CLAIMS = "Missing or expired refresh token claims";
    public static final String INVALID_REFRESH_TOKEN_TYPE = "Invalid refresh token type";

    // Request validation
    public static final String INVALID_REQUEST_BODY = "Request body is missing or contains invalid JSON";
    public static final String EMAIL_REQUIRED = "Email is required";
    public static final String INVALID_EMAIL = "Email must be valid";
    public static final String EMAIL_TOO_LONG = "Email must not exceed 255 characters";
    public static final String PASSWORD_REQUIRED = "Password is required";
    public static final String EMPLOYEE_ID_REQUIRED = "Employee ID is required";
    public static final String INVALID_ACTIVATION_PASSWORD =
            "Password must contain at least 12 characters and at most 72 UTF-8 bytes";

    // Account provisioning and activation
    public static final String EMPLOYEE_PROVISIONING_REQUIRED = "Request account provisioning through Employee";
    public static final String EMAIL_ALREADY_EXISTS = "Email already exists";
    public static final String EMPLOYEE_ACCOUNT_ALREADY_EXISTS = "Employee already has an account";
    public static final String ACCOUNT_NOT_FOUND = "Account not found";
    public static final String ACCOUNT_NOT_AWAITING_ACTIVATION = "Account is not awaiting activation";
    public static final String ACTIVATION_INVITATION_NOT_FOUND = "Activation invitation not found";
    public static final String ACTIVATION_DISABLED = "Account activation is disabled";
    public static final String INVALID_ACTIVATION_TOKEN = "Invalid or expired activation token";
    public static final String INVALID_ACCOUNT_REQUEST_ENVELOPE = "Invalid account request envelope";
    public static final String ACCOUNT_REQUEST_IDENTITY_CONFLICT =
            "Account request identity reused with different data";
    public static final String ACCOUNT_REQUEST_KEY_MISMATCH = "Account request key does not match employeeId";

    // Account management identifiers consumed by frontend translations; keep values stable
    public static final String ACCOUNT_SELF_CHANGE_FORBIDDEN = "ACCOUNT_SELF_CHANGE_FORBIDDEN";
    public static final String ACCOUNT_VERSION_CONFLICT = "ACCOUNT_VERSION_CONFLICT";
    public static final String ACCOUNT_UNEXPECTED_ROLES = "ACCOUNT_UNEXPECTED_ROLES";
    public static final String ACCOUNT_ALREADY_DISABLED = "ACCOUNT_ALREADY_DISABLED";
    public static final String ACCOUNT_NOT_DISABLED = "ACCOUNT_NOT_DISABLED";
    public static final String ACCOUNT_ROLES_REQUIRED = "ACCOUNT_ROLES_REQUIRED";
    public static final String ACCOUNT_ROLES_UNCHANGED = "ACCOUNT_ROLES_UNCHANGED";

    // Configuration and internal failures
    public static final String INVALID_ACTIVATION_CREDENTIALS =
            "Activation requires a base64 secret of at least 32 bytes, RESEND_API_KEY and RESEND_FROM";
    public static final String INVALID_ACTIVATION_FRONTEND_URL =
            "Activation frontend URL must use HTTPS (HTTP allowed only for localhost) without query or fragment";
    public static final String INVALID_ACTIVATION_TIMING =
            "Activation TTL must be positive and at most 7 days; resend cooldown must be at least 30 seconds";
    public static final String ACTIVATION_TOKEN_DERIVATION_FAILED = "Cannot derive activation token";
    public static final String SHA256_UNAVAILABLE = "SHA-256 is unavailable";
    public static final String JWT_DISTINCT_KEY_PAIRS_REQUIRED =
            "JWT access and refresh must use different RSA key pairs";
    public static final String RSA_PRIVATE_KEY_REQUIRED = "RSA private key is required";
    public static final String RSA_PUBLIC_KEY_REQUIRED = "RSA public key is required";
    public static final String RSA_KEY_TOO_SHORT = "RSA keys must be at least 2048 bits";
    public static final String RSA_KEY_PAIR_MISMATCH = "RSA private and public keys do not match";
    public static final String JWT_PRIVATE_KEY_REQUIRED = "jwt.%s-private-key is required";
    public static final String JWT_PUBLIC_KEY_REQUIRED = "jwt.%s-public-key is required";
    public static final String INVALID_JWT_KEY_PAIR =
            "Invalid JWT %s key pair: configure matching RSA PEM keys (PKCS#8 private, X.509 public)";
}
