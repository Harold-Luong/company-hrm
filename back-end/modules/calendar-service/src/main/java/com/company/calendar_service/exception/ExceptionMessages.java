package com.company.calendar_service.exception;

/** Service-owned exception messages. Preserve API text compatibility when editing. */
public final class ExceptionMessages {
    private ExceptionMessages() {
    }

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

    // CalendarErrors
    public static final String EVENT_VERSION_CONFLICT = "Event has changed; reload before retrying";
    public static final String DATA_CONFLICT = "Calendar data conflicts with stored constraints";
    public static final String DATABASE_UNAVAILABLE = "Calendar data is temporarily unavailable";

    // CalendarController
    public static final String INVALID_CALENDAR_YEAR = "year must be between 1900 and 2100";

    // CalendarManagementService
    public static final String INVALID_DATE_RANGE_OR_PAGINATION =
            "Invalid date range or pagination (maximum 366 days, size 1-100)";
    public static final String EVENT_CANCELLED = "Cancelled events cannot be changed";
    public static final String PUBLISHED_CLASSIFICATION_IMMUTABLE = "Published event classification cannot be changed";
    public static final String EVENT_NOT_DRAFT_FOR_PUBLISH = "Only draft events can be published";
    public static final String EVENT_IN_PAST = "Cannot publish an event in the past";
    public static final String EVENT_NOT_PUBLISHED = "Only published events can be cancelled";
    public static final String EVENT_NOT_DRAFT_FOR_DELETE =
            "Only draft events can be deleted; cancel published events instead";
    public static final String EVENT_NOT_FOUND = "Calendar event not found";
    public static final String IF_MATCH_REQUIRED = "If-Match is required";
    public static final String INVALID_IF_MATCH = "If-Match must contain one quoted non-negative version";
    public static final String INVALID_EVENT_RANGE = "Invalid all-day or timed event range";
    public static final String INVALID_HOLIDAY_EVENT = "Holiday events require allDay and holidayKind";
    public static final String UNEXPECTED_HOLIDAY_KIND = "holidayKind is only allowed for holidays";
    public static final String EVENT_ALREADY_STARTED = "Events that have started cannot be changed or cancelled";
    public static final String REASON_REQUIRED = "A reason is required";

    // Parameterized messages
    public static final String UNSUPPORTED_REQUEST_FIELD = "Unsupported request field: %s";
}
