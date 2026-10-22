package com.company.leave.request;

import com.company.leave.exception.ExceptionMessages;
import com.company.leave.notifications.NotificationOutbox;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import static com.company.leave.request.LeaveModels.*;

@Service
public class LeaveAdjustmentService {
    private final LeaveService leaves;
    private final LeaveRepository repository;
    private final NotificationOutbox notifications;

    private final LeaveCalendarStore calendars;

    public LeaveAdjustmentService(LeaveService leaves, LeaveRepository repository, NotificationOutbox notifications, LeaveCalendarStore calendars) {
        this.calendars = calendars;
        this.leaves = leaves; this.repository = repository; this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public List<Adjustment> list(UUID requestId, JwtAuthenticationToken actor) {
        leaves.get(requestId, actor);
        return repository.adjustments(requestId);
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Page inbox(int page, int size, JwtAuthenticationToken actor) {
        if (!LeaveService.reviewer(actor)) throw error(HttpStatus.FORBIDDEN, ExceptionMessages.ACCESS_DENIED);
        if (page < 0 || size < 1 || size > 100) throw error(HttpStatus.BAD_REQUEST, ExceptionMessages.INVALID_PAGINATION);
        return repository.list(null, Status.APPROVED, page, size, true);
    }

    @Transactional
    public Request submit(UUID id, String match, AdjustmentSubmission body, JwtAuthenticationToken actor) {
        Request request = lock(id, match, actor);
        if (!request.employeeId().equals(LeaveService.employee(actor)))
            throw error(HttpStatus.FORBIDDEN, ExceptionMessages.ADJUSTMENT_REQUESTER_REQUIRED);
        if (request.adjustmentPending()) throw error(HttpStatus.CONFLICT, ExceptionMessages.ADJUSTMENT_ALREADY_PENDING);
        int units = remainingUnits(request, body);
        repository.insertAdjustment(request, body, units, actor.getName());
        return record(request, LeaveHistoryAction.ADJUSTMENT_SUBMITTED, body.reason().trim(), actor);
    }

    @Transactional
    public Request decide(UUID requestId, UUID adjustmentId, String match, Status target, String note,
            JwtAuthenticationToken actor) {
        Request request = lock(requestId, match, actor);
        Adjustment change = repository.adjustments(requestId).stream().filter(a -> a.id().equals(adjustmentId))
                .findFirst().orElseThrow(() -> error(HttpStatus.NOT_FOUND, ExceptionMessages.ADJUSTMENT_NOT_FOUND));
        if (target == Status.CANCELLED) {
            if (!request.employeeId().equals(LeaveService.employee(actor)))
                throw error(HttpStatus.FORBIDDEN, ExceptionMessages.WITHDRAWAL_REQUESTER_REQUIRED);
        } else {
            if (!LeaveService.reviewer(actor)) throw error(HttpStatus.FORBIDDEN, ExceptionMessages.ACCESS_DENIED);
            if (request.employeeId().equals(LeaveService.employee(actor))
                    || request.requesterUserId().equals(actor.getName()) || change.requesterUserId().equals(actor.getName()))
                throw error(HttpStatus.FORBIDDEN, ExceptionMessages.SELF_REVIEW_FORBIDDEN);
        }
        if (target != Status.APPROVED && target != Status.REJECTED && target != Status.CANCELLED)
            throw error(HttpStatus.BAD_REQUEST, ExceptionMessages.INVALID_ADJUSTMENT_DECISION);
        if (change.status() != Status.PENDING) throw error(HttpStatus.CONFLICT, ExceptionMessages.ADJUSTMENT_ALREADY_PROCESSED);
        String cleanNote = note == null ? null : note.trim();
        if (target == Status.REJECTED && (cleanNote == null || cleanNote.isEmpty()))
            throw error(HttpStatus.BAD_REQUEST, ExceptionMessages.REJECTION_REASON_REQUIRED);
        if (cleanNote != null && cleanNote.length() > 2000) throw error(HttpStatus.BAD_REQUEST, ExceptionMessages.NOTE_TOO_LONG);
        if (target == Status.APPROVED) {
            int units = remainingUnits(request, new AdjustmentSubmission(change.cancelAll(), change.startDate(),
                    change.endDate(), change.period(), change.reason()));
            int refund = request.totalUnits() - units;
            if (request.leaveType() == LeaveType.ANNUAL
                    && !repository.refundAnnual(request.employeeId(), request.startDate().getYear(), refund))
                throw error(HttpStatus.CONFLICT, ExceptionMessages.BALANCE_RECONCILIATION_REQUIRED);
            repository.applyAdjustment(request, change);
        }
        if (!repository.decideAdjustment(change.id(), target, actor.getName(), cleanNote))
            throw error(HttpStatus.CONFLICT, ExceptionMessages.ADJUSTMENT_ALREADY_PROCESSED);
        return record(request, LeaveHistoryAction.forAdjustmentDecision(target), cleanNote, actor);
    }

    private Request lock(UUID id, String match, JwtAuthenticationToken actor) {
        Request current = leaves.get(id, actor);
        // Same lock as submissions and balance changes. Re-read AFTER acquiring it.
        repository.lockEmployee(current.employeeId());
        Request request = leaves.get(id, actor);
        if (match == null) throw error(HttpStatus.PRECONDITION_REQUIRED, ExceptionMessages.IF_MATCH_REQUIRED);
        if (!match.equals("\"" + request.version() + "\""))
            throw error(HttpStatus.PRECONDITION_FAILED, ExceptionMessages.LEAVE_VERSION_CONFLICT);
        if (request.status() != Status.APPROVED) throw error(HttpStatus.CONFLICT, ExceptionMessages.LEAVE_NOT_APPROVED);
        return request;
    }

    private int remainingUnits(Request request, AdjustmentSubmission body) {
        if (body.cancelAll() == null || body.reason() == null || body.reason().isBlank() || body.reason().length() > 2000)
            throw error(HttpStatus.BAD_REQUEST, ExceptionMessages.ADJUSTMENT_REASON_REQUIRED);
        if (body.cancelAll()) {
            if (body.startDate() != null || body.endDate() != null || body.period() != null)
                throw error(HttpStatus.BAD_REQUEST, ExceptionMessages.FULL_CANCELLATION_RETAINS_DATES);
            return 0;
        }
        if (body.startDate() == null || body.endDate() == null || body.period() == null
                || body.endDate().isBefore(body.startDate()) || body.startDate().isBefore(request.startDate())
                || body.endDate().isAfter(request.endDate())
                || (body.period() != LeavePeriod.FULL_DAY && !body.startDate().equals(body.endDate()))
                || (request.period() != LeavePeriod.FULL_DAY && body.period() != request.period()))
            throw error(HttpStatus.BAD_REQUEST, ExceptionMessages.INVALID_REMAINING_LEAVE_RANGE);
        // Preserve the basis actually charged at approval, including legacy calendar-day requests.
        int units = calendars.find(request.id()).map(days -> days.stream()
            .filter(d -> !d.date().isBefore(body.startDate()) && !d.date().isAfter(body.endDate()))
            .mapToInt(d -> d.units(body.period())).sum()).orElseGet(() -> body.period() == LeavePeriod.FULL_DAY
                ? Math.toIntExact(ChronoUnit.DAYS.between(body.startDate(), body.endDate()) + 1) * 2 : 1);
        if (units == 0) throw error(HttpStatus.BAD_REQUEST, ExceptionMessages.FULL_CANCELLATION_REQUIRED);
        if (units >= request.totalUnits()) throw error(HttpStatus.BAD_REQUEST, ExceptionMessages.ADJUSTMENT_MUST_REDUCE_LEAVE);
        return units;
    }

    private Request record(Request request, LeaveHistoryAction action, String note, JwtAuthenticationToken actor) {
        repository.bumpVersion(request.id());
        repository.audit(request.id(), action, actor.getName(), note);
        // Existing event contract: refresh consumers without requiring new Kafka action enums.
        notifications.append(request.id(), request.version() + 1, request.requesterUserId(), request.employeeId(),
                actor.getName(), "UPDATED");
        return repository.find(request.id()).orElseThrow();
    }

    private static ResponseStatusException error(HttpStatus status, String detail) {
        return new ResponseStatusException(status, detail);
    }
}
