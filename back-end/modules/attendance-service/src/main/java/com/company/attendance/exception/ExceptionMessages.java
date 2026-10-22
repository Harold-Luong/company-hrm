package com.company.attendance.exception;

/** Service-owned exception messages. Preserve API text compatibility when editing. */
public final class ExceptionMessages {
    private ExceptionMessages() {
    }

    // AttendanceRequestService
    public static final String PAST_REQUEST_NOT_EDITABLE = "Past requests cannot be edited";
    public static final String ATTENDANCE_SELF_REVIEW_FORBIDDEN = "You cannot review your own attendance request";
    public static final String REQUEST_NOT_PENDING_REVIEW = "Only pending requests can be reviewed";
    public static final String INVALID_REVIEW_DECISION = "Decision must be APPROVED or REJECTED";
    public static final String REJECTION_REASON_REQUIRED = "A rejection reason is required";
    public static final String EMPLOYEE_INELIGIBLE_ON_REQUEST_DATE = "Employee is not eligible on the request date";
    public static final String INVALID_REQUEST_DATE = "Request date must be today or within the next 365 days";
    public static final String REQUEST_SCHEDULE_MISSING = "No assigned schedule for request date";
    public static final String REQUEST_SCHEDULE_CHANGED = "Assigned schedule changed; reload request";
    public static final String REQUEST_PERIOD_OUTSIDE_SHIFT = "Requested period is not in the assigned shift";
    public static final String INVALID_EXPECTED_TIME =
            "Expected time must be strictly inside the work interval at minute precision";
    public static final String REQUEST_ON_HOLIDAY = "Cannot request time on a company holiday";
    public static final String LEAVE_COVERS_REQUEST_PERIOD = "A leave request already covers this period";
    public static final String DUPLICATE_ATTENDANCE_REQUEST =
            "An active request already exists for this date, period and type";
    public static final String REQUESTS_COVER_ENTIRE_INTERVAL =
            "Late and early requests cannot cover the entire work interval";
    public static final String ATTENDANCE_REQUEST_NOT_FOUND = "Attendance request not found";
    public static final String REQUEST_OWNER_REQUIRED = "Only the owner may edit or cancel a request";
    public static final String REQUEST_NOT_PENDING_EDIT = "Only pending requests can be edited or cancelled";

    // WorkforceClient
    public static final String EMPLOYEE_DATA_INCOMPLETE = "Employee returned incomplete data";
    public static final String EMPLOYEE_SCOPE_TOO_LARGE = "Select a smaller employee scope";
    public static final String CALENDAR_DATA_INCOMPLETE = "Calendar returned incomplete data";
    public static final String SOURCE_ACCESS_DENIED = "Source service denied access";
    public static final String SOURCE_RESOURCE_NOT_FOUND = "Source resource not found";
    public static final String SOURCE_UNAVAILABLE = "Source service is unavailable";
    public static final String SOURCE_DATA_MISSING = "Source returned no data";
    public static final String SOURCE_REQUEST_INTERRUPTED = "Source request interrupted";
    public static final String SOURCE_DATA_UNAVAILABLE = "Source service is unavailable or returned invalid data";

    // AttendanceCorrectionService
    public static final String CORRECTION_SELF_REVIEW_FORBIDDEN = "You cannot review your own attendance correction";
    public static final String CORRECTION_NOT_PENDING_REVIEW = "Only pending corrections can be reviewed";
    public static final String CORRECTION_ALREADY_PENDING = "A pending correction already exists for this date";
    public static final String CORRECTION_DATE_IN_FUTURE = "Correction date cannot be in the future";
    public static final String CORRECTION_ATTENDANCE_CHANGED =
            "Attendance changed; update the correction before approval";
    public static final String CORRECTION_SCHEDULE_MISSING = "No assigned schedule for correction date";
    public static final String CORRECTION_SCHEDULE_CHANGED = "Assigned schedule changed; update the correction";
    public static final String CORRECTION_BEFORE_SHIFT_END = "Corrections can only be submitted after the shift ends";
    public static final String INVALID_CORRECTED_TIMES =
            "Corrected times must be ordered, not in the future and inside the recording window (whole seconds)";
    public static final String CORRECTED_TIMES_UNCHANGED = "Corrected times are unchanged";
    public static final String EMPLOYEE_INELIGIBLE_ON_CORRECTION_DATE =
            "Employee is not eligible on the correction date";
    public static final String CORRECTION_LEAVE_OR_HOLIDAY_CONFLICT =
            "Corrected times conflict with leave or company holidays";
    public static final String CORRECTION_NOT_FOUND = "Attendance correction not found";
    public static final String CORRECTION_OWNER_REQUIRED = "Only the owner may edit or cancel a correction";
    public static final String CORRECTION_NOT_PENDING_EDIT = "Only pending corrections can be edited or cancelled";

    // AttendancePeriodService
    public static final String INVALID_PERIOD_MONTH = "Month must be YYYY-MM between 2000 and 9998";
    public static final String PERIOD_NOT_CLOSED = "Period must be closed before checking sources";
    public static final String INVALID_PERIOD_REASON = "A reason of at most 2000 characters is required";
    public static final String PERIOD_ALREADY_CLOSED = "Period is already closed";
    public static final String PERIOD_ALREADY_OPEN = "Period is already open";
    public static final String PERIOD_MONTH_NOT_COMPLETED = "Only completed months can be closed";
    public static final String PENDING_ATTENDANCE_REQUESTS =
            "Resolve pending attendance, correction and OT requests before closing";
    public static final String OT_RECORDINGS_INCOMPLETE = "Complete approved OT recordings before closing";
    public static final String PENDING_LEAVE_REQUESTS = "Resolve pending leave and leave adjustments before closing";
    public static final String NO_EMPLOYEES_TO_CLOSE = "There are no employees to close";
    public static final String UNRESOLVED_ATTENDANCE_CONFLICTS =
            "Resolve missing punches and source conflicts before closing";
    public static final String PERIOD_SOURCES_CHANGED = "Leave or holidays changed during closing; reload and retry";

    // PeriodStore
    public static final String PERIOD_CLOSED = "Attendance period is closed; HR/ADMIN must reopen it with a reason";
    public static final String SCHEDULE_OVERLAPS_CLOSED_PERIOD = "Schedule overlaps a closed attendance period";

    // AttendanceService
    public static final String PREVIOUS_SHIFT_INCOMPLETE = "Finish the previous overnight shift first";
    public static final String EMPLOYEE_INELIGIBLE_FOR_ATTENDANCE = "Employee is not eligible to record attendance";
    public static final String ATTENDANCE_ALREADY_RECORDED = "Attendance has already been recorded for this day";
    public static final String TODAY_SCHEDULE_MISSING = "No assigned schedule for today";
    public static final String NON_WORKING_DAY_OR_LEAVE_CONFLICT =
            "Today is a non-working day or leave coverage conflicts";
    public static final String CHECK_IN_REQUIRED = "Check-in is required first";
    public static final String ALREADY_CHECKED_OUT = "Already checked out";
    public static final String INVALID_CHECK_OUT_ORDER = "Check-out must be after check-in";
    public static final String FUTURE_REPORT_DATES = "Attendance reports cannot include future dates";
    public static final String REPORT_TOO_LARGE = "Report exceeds 5000 rows; narrow the scope";
    public static final String REPORT_SCOPE_TOO_LARGE = "Report exceeds 5000 rows; narrow the date or employee scope";
    public static final String RECORDED_DAY_NOT_FOUND = "Recorded day not found";
    public static final String OUTSIDE_RECORDING_WINDOW = "Outside the assigned recording window";

    // ScheduleService
    public static final String SCHEDULE_ATTENDANCE_REQUEST_CONFLICT =
            "Schedule affects approved attendance requests; resolve conflicts first";
    public static final String SCHEDULE_ATTENDANCE_OR_LEAVE_CONFLICT =
            "Schedule affects recorded attendance or approved leave; resolve conflicts first";
    public static final String SCHEDULE_OT_CONFLICT = "Schedule affects approved OT; choose another date range";
    public static final String OVERLAPPING_SCHEDULE_RULES = "Overlapping schedule rules require correction";
    public static final String INVALID_DATE_RANGE = "Date range must be ordered and at most 366 days";
    public static final String INVALID_SCHEDULE_DATE_RANGE =
            "Schedule changes must start today or later, with an ordered date range";
    public static final String INVALID_SELECTED_EMPLOYEE_IDS = "Employee IDs are required only for SELECTED_EMPLOYEES";
    public static final String INACTIVE_SHIFT_ASSIGNMENT = "Inactive shift cannot be assigned";
    public static final String SHIFT_VERSION_CONFLICT = "Shift changed; reload preview";
    public static final String SELECTED_EMPLOYEE_INELIGIBLE = "Selected employee is not eligible on the start date";
    public static final String ADJACENT_SHIFT_OVERLAP = "Schedule overlaps an adjacent assigned shift";

    // OvertimeService
    public static final String INVALID_OT_INTERVAL =
            "OT must be in the future, at minute precision, and shorter than 24 hours";
    public static final String EMPLOYEE_INELIGIBLE_FOR_OT = "Employee is not eligible for OT";
    public static final String OT_REQUEST_OVERLAP = "OT overlaps another pending or approved request";
    public static final String OT_LEAVE_CONFLICT = "OT conflicts with leave";
    public static final String OT_OVERLAPS_WORKING_INTERVAL = "OT must be outside regular working intervals";
    public static final String OT_SELF_REVIEW_FORBIDDEN = "You cannot review your own OT request";
    public static final String OT_NOT_APPROVED = "OT must be approved before recording time";
    public static final String OT_ALREADY_STARTED = "OT already started";
    public static final String OUTSIDE_OT_INTERVAL = "Outside the approved OT interval";
    public static final String INVALID_OT_RECORDING = "OT check-in is required and check-out must be unique";
    public static final String OT_REQUEST_NOT_FOUND = "OT request not found";
    public static final String OT_OWNER_REQUIRED = "Only the owner may record or cancel OT";
    public static final String OT_NOT_PENDING = "Only pending OT requests can be changed";

    // ShiftService
    public static final String SHIFT_NOT_FOUND = "Shift not found";
    public static final String SHIFT_REVISION_NOT_FOUND = "Shift revision not found";
    public static final String SHIFT_ALREADY_INACTIVE = "Shift is already inactive";
    public static final String UNSUPPORTED_SHIFT_TYPE = "Only FIXED_SHIFT is supported in this release";
    public static final String UNSUPPORTED_TIMEZONE = "Only Asia/Ho_Chi_Minh is supported in this release";
    public static final String INVALID_OVERNIGHT_SHIFT =
            "Overnight shift requires one interval and a recording window shorter than 24 hours";
    public static final String INVALID_SHIFT_INTERVALS =
            "Intervals must be ordered, non-overlapping and have unique periods, at minute precision";
    public static final String INVALID_SHIFT_RECORDING_WINDOW =
            "The recording window must cover the entire same-day shift";

    // OperationService
    public static final String IDEMPOTENCY_KEY_REQUIRED = "A 1-100 character Idempotency-Key is required";
    public static final String IDEMPOTENCY_REQUEST_CONFLICT = "Idempotency-Key was used for a different request";
    public static final String IDEMPOTENCY_RESPONSE_EXPIRED =
            "Idempotency response expired; the original operation will not be repeated";

    // ApiRules
    public static final String REVIEWER_ROLE_REQUIRED = "HR or ADMIN role is required";
    public static final String IF_MATCH_REQUIRED = "If-Match is required";
    public static final String RESOURCE_VERSION_CONFLICT = "Resource changed; reload before retrying";
    public static final String INVALID_PAGINATION = "Invalid pagination";

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

    // CorporateNetwork
    public static final String TRUSTED_CLIENT_IP_REQUIRED = "A trusted single client IP is required";
    public static final String COMPANY_NETWORK_REQUIRED = "Check-in/out is only allowed from the company network";

    // NotificationOutbox
    public static final String NOTIFICATION_TRANSACTION_REQUIRED = "Notification must be recorded in a transaction";

    // GlobalExceptionHandler
    public static final String DATA_CONFLICT = "Data conflicts with a concurrent change or database constraint";
    public static final String DATABASE_UNAVAILABLE = "Attendance database is unavailable";
    public static final String REQUEST_VALIDATION_FAILED = "Request validation failed";

    // WorkTimePolicy
    public static final String INVALID_STANDARD_DAY_MINUTES = "standardDayMinutes must be 1..1440";
    public static final String INVALID_DEVIATION_ROUNDING_MINUTES = "deviationRoundingMinutes must be 1..1440";

    // WorkTimeInput
    public static final String FULL_DAY_RESERVED_FOR_LEAVE = "FULL_DAY is reserved for leave";
    public static final String INVALID_INTERVAL_DURATION = "Intervals must be positive and at most 24 hours";
    public static final String INVALID_SCHEDULED_INTERVAL_PRECISION = "Scheduled intervals must have minute precision";

    // WorkTimeSupport
    public static final String INCONSISTENT_PERIOD_POLICY = "Period inputs must use the same policy and timezone";
    public static final String INVALID_WORK_INTERVAL_ORDER = "Work intervals must be ordered and non-overlapping";
    public static final String INVALID_SHIFT_DATE_RANGE = "Shift must start on workDate and span at most 24 hours";
    public static final String INCOMPLETE_RECORDING_CUTOFF = "Recording cutoff must cover the complete shift";
    public static final String INVALID_OT_WORK_DATE = "OT must be assigned to its starting workDate";

    // Parameterized messages
    public static final String REQUIRED_TEXT = "%s must not be blank";
    public static final String DUPLICATE_WORK_DATE = "Duplicate work date: %s";
    public static final String DUPLICATE_PERIOD_OT_ID = "Duplicate OT id in period: %s";
    public static final String OVERLAPPING_WORK_DATES = "Overlapping shift/OT across work dates: %s and %s";
    public static final String DUPLICATE_WORK_PERIOD = "Duplicate work period: %s";
    public static final String DUPLICATE_LEAVE_ID = "Duplicate leave id: %s";
    public static final String DUPLICATE_OT_ID = "Duplicate OT id: %s";
}
