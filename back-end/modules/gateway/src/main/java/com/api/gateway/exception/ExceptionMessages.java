package com.api.gateway.exception;

/** Service-owned exception messages. Preserve API text compatibility when editing. */
public final class ExceptionMessages {
    private ExceptionMessages() {
    }

    // Security
    public static final String JWT_PUBLIC_KEY_REQUIRED = "JWT access public key is required";
    public static final String RSA_KEY_TOO_SHORT = "RSA key must be at least 2048 bits";
    public static final String SESSION_REPLACED = "Session replaced or signed out";
    public static final String SESSION_VERIFICATION_UNAVAILABLE = "Session verification is unavailable";
    public static final String INVALID_ACCESS_TOKEN_CLAIMS = "Invalid access token claims";
}
