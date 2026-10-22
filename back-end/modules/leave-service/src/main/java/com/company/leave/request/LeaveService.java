package com.company.leave.request;

import com.company.leave.exception.ExceptionMessages;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import static com.company.leave.request.LeaveModels.*;

@Service
public class LeaveService {
    static final int ANNUAL_ENTITLEMENT_UNITS = 24;
    private final LeaveRepository repository;
    private final Clock clock;
    private final com.company.leave.notifications.NotificationOutbox notifications;

    private final LeaveCalendarClient calendar;
    private final LeaveCalendarStore calendars;

    public LeaveService(LeaveRepository repository, Clock clock, com.company.leave.notifications.NotificationOutbox notifications,
            LeaveCalendarClient calendar, LeaveCalendarStore calendars) {
        this.calendar = calendar; this.calendars = calendars;
        this.repository = repository;
        this.clock = clock;
        this.notifications = notifications;
    }

    static UUID employee(JwtAuthenticationToken actor) {
        return UUID.fromString(actor.getToken().getClaimAsString("employee_id"));
    }

    static boolean reviewer(JwtAuthenticationToken actor) {
        return actor.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_HR") || a.getAuthority().equals("ROLE_ADMIN"));
    }

    private static ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }

    public record Estimate(int totalUnits, java.math.BigDecimal totalDays, java.util.List<LeaveCalendarClient.Day> days) {}
    public Estimate estimate(LocalDate from, LocalDate until, LeavePeriod period, JwtAuthenticationToken actor) {
        if (from == null || until == null || period == null || until.isBefore(from)
                || ChronoUnit.DAYS.between(from, until) >= 366 || from.isBefore(LocalDate.now(clock))
                || (period != LeavePeriod.FULL_DAY && !from.equals(until)))
            throw error(HttpStatus.BAD_REQUEST, ExceptionMessages.INVALID_LEAVE_DATES_OR_PERIOD);
        var days = calendar.calendar(employee(actor), from, until, actor);
        int units = days.stream().mapToInt(d -> d.units(period)).sum();
        return new Estimate(units, java.math.BigDecimal.valueOf(units).divide(java.math.BigDecimal.valueOf(2)), days);
    }

    @Transactional
    public Request submit(Submission body, JwtAuthenticationToken actor) {
        if (body.startDate().isBefore(LocalDate.now(clock)) || body.endDate().isBefore(body.startDate())
                || ChronoUnit.DAYS.between(body.startDate(), body.endDate()) >= 366) {
            throw error(HttpStatus.BAD_REQUEST,
                    ExceptionMessages.INVALID_LEAVE_DATE_RANGE);
        }
        LeavePeriod period = body.period() == null ? LeavePeriod.FULL_DAY : body.period();
        if (period != LeavePeriod.FULL_DAY && !body.startDate().equals(body.endDate())) {
            throw error(HttpStatus.BAD_REQUEST, ExceptionMessages.HALF_DAY_REQUIRES_SINGLE_DATE);
        }
        if (body.leaveType() == LeaveType.ANNUAL && body.startDate().getYear() != body.endDate().getYear()) {
            throw error(HttpStatus.BAD_REQUEST, ExceptionMessages.ANNUAL_LEAVE_SPANS_YEARS);
        }
        UUID employeeId = employee(actor);
        var calendarDays = calendar.calendar(employeeId, body.startDate(), body.endDate(), actor);
        int totalUnits = calendarDays.stream().mapToInt(d -> d.units(period)).sum();
        if (totalUnits == 0) throw error(HttpStatus.BAD_REQUEST, ExceptionMessages.NO_WORKING_LEAVE_TIME);
        repository.lockEmployee(employeeId);
        if (repository.overlaps(employeeId, body.startDate(), body.endDate(), period)) {
            throw error(HttpStatus.CONFLICT, ExceptionMessages.OVERLAPPING_LEAVE_REQUEST);
        }
        Request request = repository.insert(employeeId, actor.getName(), body, period, totalUnits);
        calendars.save(request.id(), calendarDays);
        repository.audit(request.id(), LeaveHistoryAction.SUBMITTED, actor.getName(), null);
        notifications.append(request.id(), request.version(), request.requesterUserId(), request.employeeId(), actor.getName(), "SUBMITTED");
        return request;
    }

    @Transactional
    public Balance balance(Integer requestedYear, JwtAuthenticationToken actor) {
        int year = requestedYear == null ? LocalDate.now(clock).getYear() : requestedYear;
        if (year < 2000 || year > 9999)
            throw error(HttpStatus.BAD_REQUEST, ExceptionMessages.INVALID_BALANCE_YEAR);
        UUID employeeId = employee(actor);
        repository.lockEmployee(employeeId);
        return repository.annualBalance(employeeId, year, ANNUAL_ENTITLEMENT_UNITS);
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Page list(boolean inbox, Status status, int page, int size, JwtAuthenticationToken actor) {
        if (inbox && !reviewer(actor))
            throw error(HttpStatus.FORBIDDEN, ExceptionMessages.ACCESS_DENIED);
        if (page < 0 || size < 1 || size > 100)
            throw error(HttpStatus.BAD_REQUEST, ExceptionMessages.INVALID_PAGINATION);
        return repository.list(inbox ? null : employee(actor), status, page, size);
    }

    @Transactional(readOnly = true)
    public List<AttendanceLeave> calendar(int year, JwtAuthenticationToken actor) {
        if (year < 2000 || year > 9999)
            throw error(HttpStatus.BAD_REQUEST, ExceptionMessages.INVALID_CALENDAR_YEAR);
        // Personal calendar stays owner-scoped, including for HR/ADMIN.
        return repository.attendance(employee(actor), LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
    }

    @Transactional(readOnly = true)
    public PendingCount pendingCount(JwtAuthenticationToken actor) {
        if (!reviewer(actor))
            throw error(HttpStatus.FORBIDDEN, ExceptionMessages.ACCESS_DENIED);
        return new PendingCount(repository.pendingCount());
    }

    @Transactional(readOnly = true)
    public List<AttendanceLeave> attendance(UUID employeeId, LocalDate from, LocalDate until,
            JwtAuthenticationToken actor) {
        if (!reviewer(actor)) {
            if (employeeId != null && !employeeId.equals(employee(actor)))
                throw error(HttpStatus.FORBIDDEN, ExceptionMessages.OWN_LEAVE_COVERAGE_REQUIRED);
            employeeId = employee(actor);
        }
        if (until.isBefore(from))
            throw error(HttpStatus.BAD_REQUEST, ExceptionMessages.INVALID_DATE_RANGE);
        var result = repository.attendance(employeeId, from, until);
        if (result.size() > 2000)
            throw error(HttpStatus.UNPROCESSABLE_CONTENT, ExceptionMessages.COVERAGE_SCOPE_TOO_LARGE);
        return result;
    }

    @Transactional(readOnly = true)
    public Request get(UUID id, JwtAuthenticationToken actor) {
        Request request = repository.find(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, ExceptionMessages.LEAVE_REQUEST_NOT_FOUND));
        if (!request.employeeId().equals(employee(actor)) && !reviewer(actor)) {
            throw error(HttpStatus.NOT_FOUND, ExceptionMessages.LEAVE_REQUEST_NOT_FOUND);
        }
        return request;
    }

    @Transactional(readOnly = true)
    public List<History> history(UUID id, JwtAuthenticationToken actor) {
        get(id, actor);
        return repository.history(id);
    }

    @Transactional
    public Request decide(UUID id, Status target, String match, String note, JwtAuthenticationToken actor) {
        if (target != Status.CANCELLED && !reviewer(actor))
            throw error(HttpStatus.FORBIDDEN, ExceptionMessages.ACCESS_DENIED);
        Request request = get(id, actor);
        if (target == Status.CANCELLED) {
            if (!request.employeeId().equals(employee(actor)))
                throw error(HttpStatus.FORBIDDEN, ExceptionMessages.CANCELLATION_REQUESTER_REQUIRED);
        } else if (request.employeeId().equals(employee(actor)) || request.requesterUserId().equals(actor.getName())) {
            throw error(HttpStatus.FORBIDDEN, ExceptionMessages.SELF_REVIEW_FORBIDDEN);
        }
        repository.lockEmployee(request.employeeId());
        request = get(id, actor);
        if (match == null)
            throw error(HttpStatus.PRECONDITION_REQUIRED, ExceptionMessages.IF_MATCH_REQUIRED);
        if (!match.equals("\"" + request.version() + "\""))
            throw error(HttpStatus.PRECONDITION_FAILED, ExceptionMessages.LEAVE_VERSION_CONFLICT);
        if (request.status() != Status.PENDING)
            throw error(HttpStatus.CONFLICT, ExceptionMessages.REQUEST_NOT_PENDING);
        String cleanNote = note == null ? null : note.trim();
        if (target == Status.REJECTED && (cleanNote == null || cleanNote.isEmpty())) {
            throw error(HttpStatus.BAD_REQUEST, ExceptionMessages.REJECTION_REASON_REQUIRED);
        }
        if (target == Status.APPROVED) {
            var original = calendars.find(id).orElseThrow(() -> error(HttpStatus.CONFLICT,
                ExceptionMessages.LEGACY_WORKING_CALENDAR_MISSING));
            var current = calendar.calendar(request.employeeId(), request.startDate(), request.endDate(), actor);
            if (!original.equals(current)) throw error(HttpStatus.CONFLICT,
                ExceptionMessages.WORKING_CALENDAR_CHANGED);
        }
        if (target == Status.APPROVED && request.leaveType() == LeaveType.ANNUAL
                && !repository.consumeAnnual(request.employeeId(), request.startDate().getYear(), request.totalUnits(),
                        ANNUAL_ENTITLEMENT_UNITS)) {
            throw error(HttpStatus.CONFLICT, ExceptionMessages.INSUFFICIENT_ANNUAL_BALANCE);
        }
        if (!repository.transition(id, request.version(), target, actor.getName(), cleanNote)) {
            throw error(HttpStatus.PRECONDITION_FAILED, ExceptionMessages.LEAVE_VERSION_CONFLICT);
        }
        repository.audit(id, LeaveHistoryAction.forDecision(target), actor.getName(), cleanNote);
        notifications.append(id, request.version() + 1, request.requesterUserId(), request.employeeId(), actor.getName(), target.name());
        return repository.find(id).orElseThrow();
    }
}
