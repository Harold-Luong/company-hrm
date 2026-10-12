package com.company.leave.request;

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

    public LeaveAdjustmentService(LeaveService leaves, LeaveRepository repository, NotificationOutbox notifications) {
        this.leaves = leaves; this.repository = repository; this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public List<Adjustment> list(UUID requestId, JwtAuthenticationToken actor) {
        leaves.get(requestId, actor);
        return repository.adjustments(requestId);
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Page inbox(int page, int size, JwtAuthenticationToken actor) {
        if (!LeaveService.reviewer(actor)) throw error(HttpStatus.FORBIDDEN, "Access is denied");
        if (page < 0 || size < 1 || size > 100) throw error(HttpStatus.BAD_REQUEST, "Invalid pagination");
        return repository.list(null, Status.APPROVED, page, size, true);
    }

    @Transactional
    public Request submit(UUID id, String match, AdjustmentSubmission body, JwtAuthenticationToken actor) {
        Request request = lock(id, match, actor);
        if (!request.employeeId().equals(LeaveService.employee(actor)))
            throw error(HttpStatus.FORBIDDEN, "Only the requester can adjust this request");
        if (request.adjustmentPending()) throw error(HttpStatus.CONFLICT, "An adjustment is already pending");
        int units = remainingUnits(request, body);
        repository.insertAdjustment(request, body, units, actor.getName());
        return record(request, "ADJUSTMENT_SUBMITTED", body.reason().trim(), actor);
    }

    @Transactional
    public Request decide(UUID requestId, UUID adjustmentId, String match, Status target, String note,
            JwtAuthenticationToken actor) {
        Request request = lock(requestId, match, actor);
        Adjustment change = repository.adjustments(requestId).stream().filter(a -> a.id().equals(adjustmentId))
                .findFirst().orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Adjustment not found"));
        if (target == Status.CANCELLED) {
            if (!request.employeeId().equals(LeaveService.employee(actor)))
                throw error(HttpStatus.FORBIDDEN, "Only the requester can withdraw an adjustment");
        } else {
            if (!LeaveService.reviewer(actor)) throw error(HttpStatus.FORBIDDEN, "Access is denied");
            if (request.employeeId().equals(LeaveService.employee(actor))
                    || request.requesterUserId().equals(actor.getName()) || change.requesterUserId().equals(actor.getName()))
                throw error(HttpStatus.FORBIDDEN, "You cannot review your own leave request");
        }
        if (target != Status.APPROVED && target != Status.REJECTED && target != Status.CANCELLED)
            throw error(HttpStatus.BAD_REQUEST, "Invalid adjustment decision");
        if (change.status() != Status.PENDING) throw error(HttpStatus.CONFLICT, "Adjustment already processed");
        String cleanNote = note == null ? null : note.trim();
        if (target == Status.REJECTED && (cleanNote == null || cleanNote.isEmpty()))
            throw error(HttpStatus.BAD_REQUEST, "A rejection reason is required");
        if (cleanNote != null && cleanNote.length() > 2000) throw error(HttpStatus.BAD_REQUEST, "Note is too long");
        if (target == Status.APPROVED) {
            int units = remainingUnits(request, new AdjustmentSubmission(change.cancelAll(), change.startDate(),
                    change.endDate(), change.period(), change.reason()));
            int refund = request.totalUnits() - units;
            if (request.leaveType() == LeaveType.ANNUAL
                    && !repository.refundAnnual(request.employeeId(), request.startDate().getYear(), refund))
                throw error(HttpStatus.CONFLICT, "Leave balance requires reconciliation before refund");
            repository.applyAdjustment(request, change);
        }
        if (!repository.decideAdjustment(change.id(), target, actor.getName(), cleanNote))
            throw error(HttpStatus.CONFLICT, "Adjustment already processed");
        return record(request, "ADJUSTMENT_" + target.name(), cleanNote, actor);
    }

    private Request lock(UUID id, String match, JwtAuthenticationToken actor) {
        Request current = leaves.get(id, actor);
        // Same lock as submissions and balance changes. Re-read AFTER acquiring it.
        repository.lockEmployee(current.employeeId());
        Request request = leaves.get(id, actor);
        if (match == null) throw error(HttpStatus.PRECONDITION_REQUIRED, "If-Match is required");
        if (!match.equals("\"" + request.version() + "\""))
            throw error(HttpStatus.PRECONDITION_FAILED, "Leave request has changed; reload before retrying");
        if (request.status() != Status.APPROVED) throw error(HttpStatus.CONFLICT, "Only approved leave can be adjusted");
        return request;
    }

    private int remainingUnits(Request request, AdjustmentSubmission body) {
        if (body.cancelAll() == null || body.reason() == null || body.reason().isBlank() || body.reason().length() > 2000)
            throw error(HttpStatus.BAD_REQUEST, "Adjustment reason is required");
        if (body.cancelAll()) {
            if (body.startDate() != null || body.endDate() != null || body.period() != null)
                throw error(HttpStatus.BAD_REQUEST, "Full cancellation cannot retain leave dates");
            return 0;
        }
        if (body.startDate() == null || body.endDate() == null || body.period() == null
                || body.endDate().isBefore(body.startDate()) || body.startDate().isBefore(request.startDate())
                || body.endDate().isAfter(request.endDate())
                || (body.period() != LeavePeriod.FULL_DAY && !body.startDate().equals(body.endDate()))
                || (request.period() != LeavePeriod.FULL_DAY && body.period() != request.period()))
            throw error(HttpStatus.BAD_REQUEST, "Remaining leave must be within the approved dates and period");
        int units = body.period() == LeavePeriod.FULL_DAY
                ? Math.toIntExact(ChronoUnit.DAYS.between(body.startDate(), body.endDate()) + 1) * 2 : 1;
        if (units >= request.totalUnits()) throw error(HttpStatus.BAD_REQUEST, "Adjustment must reduce approved leave");
        return units;
    }

    private Request record(Request request, String action, String note, JwtAuthenticationToken actor) {
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
