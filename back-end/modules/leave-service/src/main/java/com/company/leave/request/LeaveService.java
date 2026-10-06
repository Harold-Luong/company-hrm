package com.company.leave.request;

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

    public LeaveService(LeaveRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
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

    @Transactional
    public Request submit(Submission body, JwtAuthenticationToken actor) {
        if (body.startDate().isBefore(LocalDate.now(clock)) || body.endDate().isBefore(body.startDate())
                || ChronoUnit.DAYS.between(body.startDate(), body.endDate()) >= 366) {
            throw error(HttpStatus.BAD_REQUEST,
                    "Dates must start today or later, in order, and span at most 366 calendar days");
        }
        LeavePeriod period = body.period() == null ? LeavePeriod.FULL_DAY : body.period();
        if (period != LeavePeriod.FULL_DAY && !body.startDate().equals(body.endDate())) {
            throw error(HttpStatus.BAD_REQUEST, "Morning or afternoon leave must be for a single date");
        }
        if (body.leaveType() == LeaveType.ANNUAL && body.startDate().getYear() != body.endDate().getYear()) {
            throw error(HttpStatus.BAD_REQUEST, "Annual leave cannot span calendar years; submit one request per year");
        }
        int totalUnits = period == LeavePeriod.FULL_DAY
                ? Math.toIntExact(ChronoUnit.DAYS.between(body.startDate(), body.endDate()) + 1) * 2 : 1;
        UUID employeeId = employee(actor);
        repository.lockEmployee(employeeId);
        if (repository.overlaps(employeeId, body.startDate(), body.endDate(), period)) {
            throw error(HttpStatus.CONFLICT, "A pending or approved leave request overlaps these dates");
        }
        Request request = repository.insert(employeeId, actor.getName(), body, period, totalUnits);
        repository.audit(request.id(), "SUBMITTED", actor.getName(), null);
        return request;
    }

    @Transactional
    public Balance balance(Integer requestedYear, JwtAuthenticationToken actor) {
        int year = requestedYear == null ? LocalDate.now(clock).getYear() : requestedYear;
        if (year < 2000 || year > 9999)
            throw error(HttpStatus.BAD_REQUEST, "Invalid balance year");
        return repository.annualBalance(employee(actor), year, ANNUAL_ENTITLEMENT_UNITS);
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Page list(boolean inbox, Status status, int page, int size, JwtAuthenticationToken actor) {
        if (inbox && !reviewer(actor))
            throw error(HttpStatus.FORBIDDEN, "Access is denied");
        if (page < 0 || size < 1 || size > 100)
            throw error(HttpStatus.BAD_REQUEST, "Invalid pagination");
        return repository.list(inbox ? null : employee(actor), status, page, size);
    }

    @Transactional(readOnly = true)
    public PendingCount pendingCount(JwtAuthenticationToken actor) {
        if (!reviewer(actor))
            throw error(HttpStatus.FORBIDDEN, "Access is denied");
        return new PendingCount(repository.pendingCount());
    }

    @Transactional(readOnly = true)
    public List<AttendanceLeave> attendance(UUID employeeId, LocalDate from, LocalDate until,
            JwtAuthenticationToken actor) {
        if (!reviewer(actor)) {
            if (employeeId != null && !employeeId.equals(employee(actor)))
                throw error(HttpStatus.FORBIDDEN, "You can only read your own leave coverage");
            employeeId = employee(actor);
        }
        if (until.isBefore(from)) throw error(HttpStatus.BAD_REQUEST, "Invalid date range");
        var result = repository.attendance(employeeId, from, until);
        if (result.size() > 2000)
            throw error(HttpStatus.UNPROCESSABLE_ENTITY, "Narrow the employee or date range");
        return result;
    }

    @Transactional(readOnly = true)
    public Request get(UUID id, JwtAuthenticationToken actor) {
        Request request = repository.find(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Leave request not found"));
        if (!request.employeeId().equals(employee(actor)) && !reviewer(actor)) {
            throw error(HttpStatus.NOT_FOUND, "Leave request not found");
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
            throw error(HttpStatus.FORBIDDEN, "Access is denied");
        Request request = get(id, actor);
        if (target == Status.CANCELLED) {
            if (!request.employeeId().equals(employee(actor)))
                throw error(HttpStatus.FORBIDDEN, "Only the requester can cancel this request");
        } else if (request.employeeId().equals(employee(actor)) || request.requesterUserId().equals(actor.getName())) {
            throw error(HttpStatus.FORBIDDEN, "You cannot review your own leave request");
        }
        if (match == null)
            throw error(HttpStatus.PRECONDITION_REQUIRED, "If-Match is required");
        if (!match.equals("\"" + request.version() + "\""))
            throw error(HttpStatus.PRECONDITION_FAILED, "Leave request has changed; reload before retrying");
        if (request.status() != Status.PENDING)
            throw error(HttpStatus.CONFLICT, "Only pending requests can be processed");
        String cleanNote = note == null ? null : note.trim();
        if (target == Status.REJECTED && (cleanNote == null || cleanNote.isEmpty())) {
            throw error(HttpStatus.BAD_REQUEST, "A rejection reason is required");
        }
        if (target == Status.APPROVED && request.leaveType() == LeaveType.ANNUAL
                && !repository.consumeAnnual(request.employeeId(), request.startDate().getYear(), request.totalUnits(),
                        ANNUAL_ENTITLEMENT_UNITS)) {
            throw error(HttpStatus.CONFLICT, "Insufficient annual leave balance");
        }
        if (!repository.transition(id, request.version(), target, actor.getName(), cleanNote)) {
            throw error(HttpStatus.PRECONDITION_FAILED, "Leave request has changed; reload before retrying");
        }
        repository.audit(id, target.name(), actor.getName(), cleanNote);
        return repository.find(id).orElseThrow();
    }
}
