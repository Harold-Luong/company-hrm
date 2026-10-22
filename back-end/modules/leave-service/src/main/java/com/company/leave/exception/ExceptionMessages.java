package com.company.leave.exception;

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

    // LeaveErrors
    public static final String DATA_CONFLICT = "Leave data conflicts with stored constraints";
    public static final String DATABASE_UNAVAILABLE = "Leave data is temporarily unavailable";

    // LeaveCalendarClient
    public static final String SCHEDULE_ACCESS_DENIED = "Schedule access denied";
    public static final String WORKING_CALENDAR_UNAVAILABLE = "Working calendar is unavailable or incomplete";

    // LeaveService
    public static final String INVALID_LEAVE_DATES_OR_PERIOD = "Invalid leave dates or period";
    public static final String INVALID_LEAVE_DATE_RANGE =
            "Dates must start today or later, in order, and span at most 366 calendar days";
    public static final String HALF_DAY_REQUIRES_SINGLE_DATE = "Morning or afternoon leave must be for a single date";
    public static final String ANNUAL_LEAVE_SPANS_YEARS =
            "Annual leave cannot span calendar years; submit one request per year";
    public static final String NO_WORKING_LEAVE_TIME = "No working time in the requested leave period";
    public static final String OVERLAPPING_LEAVE_REQUEST = "A pending or approved leave request overlaps these dates";
    public static final String INVALID_BALANCE_YEAR = "Invalid balance year";
    public static final String INVALID_PAGINATION = "Invalid pagination";
    public static final String INVALID_CALENDAR_YEAR = "Invalid calendar year";
    public static final String OWN_LEAVE_COVERAGE_REQUIRED = "You can only read your own leave coverage";
    public static final String INVALID_DATE_RANGE = "Invalid date range";
    public static final String COVERAGE_SCOPE_TOO_LARGE = "Narrow the employee or date range";
    public static final String LEAVE_REQUEST_NOT_FOUND = "Leave request not found";
    public static final String CANCELLATION_REQUESTER_REQUIRED = "Only the requester can cancel this request";
    public static final String SELF_REVIEW_FORBIDDEN = "You cannot review your own leave request";
    public static final String IF_MATCH_REQUIRED = "If-Match is required";
    public static final String LEAVE_VERSION_CONFLICT = "Leave request has changed; reload before retrying";
    public static final String REQUEST_NOT_PENDING = "Only pending requests can be processed";
    public static final String REJECTION_REASON_REQUIRED = "A rejection reason is required";
    public static final String LEGACY_WORKING_CALENDAR_MISSING =
            "Legacy request has no working calendar; withdraw and resubmit before approval";
    public static final String WORKING_CALENDAR_CHANGED =
            "Working calendar changed; withdraw and resubmit leave before approval";
    public static final String INSUFFICIENT_ANNUAL_BALANCE = "Insufficient annual leave balance";

    // LeaveHistoryAction
    public static final String INVALID_LEAVE_DECISION = "Pending is not a leave decision";
    public static final String INVALID_ADJUSTMENT_STATUS = "Pending is not an adjustment decision";

    // LeaveAdjustmentService
    public static final String ADJUSTMENT_REQUESTER_REQUIRED = "Only the requester can adjust this request";
    public static final String ADJUSTMENT_ALREADY_PENDING = "An adjustment is already pending";
    public static final String ADJUSTMENT_NOT_FOUND = "Adjustment not found";
    public static final String WITHDRAWAL_REQUESTER_REQUIRED = "Only the requester can withdraw an adjustment";
    public static final String INVALID_ADJUSTMENT_DECISION = "Invalid adjustment decision";
    public static final String ADJUSTMENT_ALREADY_PROCESSED = "Adjustment already processed";
    public static final String NOTE_TOO_LONG = "Note is too long";
    public static final String BALANCE_RECONCILIATION_REQUIRED = "Leave balance requires reconciliation before refund";
    public static final String LEAVE_NOT_APPROVED = "Only approved leave can be adjusted";
    public static final String ADJUSTMENT_REASON_REQUIRED = "Adjustment reason is required";
    public static final String FULL_CANCELLATION_RETAINS_DATES = "Full cancellation cannot retain leave dates";
    public static final String INVALID_REMAINING_LEAVE_RANGE =
            "Remaining leave must be within the approved dates and period";
    public static final String FULL_CANCELLATION_REQUIRED = "Use full cancellation when no working leave remains";
    public static final String ADJUSTMENT_MUST_REDUCE_LEAVE = "Adjustment must reduce approved leave";

    // NotificationOutbox
    public static final String NOTIFICATION_TRANSACTION_REQUIRED = "Notification must be recorded in a transaction";
}
