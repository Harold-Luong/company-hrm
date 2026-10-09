package com.company.attendance.service;

import com.company.attendance.dto.*;
import com.company.attendance.dto.AttendanceCorrectionModels.*;
import com.company.attendance.dto.AttendanceRequestModels.Decision;
import com.company.attendance.entity.*;
import com.company.attendance.enums.AttendanceRequestStatus;
import com.company.attendance.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import static com.company.attendance.service.ApiRules.*;
import static com.company.attendance.enums.AttendanceRequestStatus.*;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class AttendanceCorrectionService {
    private final AttendanceCorrectionRepository requests;
    private final AttendanceCorrectionHistoryRepository history;
    private final AttendanceDayRepository days;
    private final ScheduleStateRepository state;
    private final ScheduleService schedules;
    private final ShiftService shifts;
    private final WorkforceClient source;
    private final AttendanceCalculator calculator;
    private final AttendanceSnapshots snapshots;
    private final OperationService operations;
    private final ObjectMapper mapper;
    private final Clock clock;

    public PageResponse<Response> list(boolean inbox, AttendanceRequestStatus status, int page, int size,
            JwtAuthenticationToken actor) {
        page(page, size);
        if (inbox) requireReviewer(actor);
        return PageResponse.from(requests.search(inbox ? null : employee(actor), status,
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))))
                .map(this::response));
    }

    public Response get(UUID id, JwtAuthenticationToken actor) { return response(read(id, actor)); }

    public PageResponse<AttendanceCorrectionHistory> history(UUID id, int page, int size, JwtAuthenticationToken actor) {
        read(id, actor); page(page, size);
        return PageResponse.from(history.findByRequestIdOrderByRequestVersionDesc(id, PageRequest.of(page, size)));
    }

    @Transactional
    public Response create(Write body, String key, JwtAuthenticationToken actor) {
        state.lockState();
        var replay = operations.replay(actor.getName(), "CREATE_ATTENDANCE_CORRECTION", key, body, Response.class);
        if (replay != null) return replay;
        var row = new AttendanceCorrection();
        row.setId(UUID.randomUUID()); row.setEmployeeId(employee(actor)); row.setRequesterUserId(actor.getName());
        row.setStatus(PENDING); row.setActiveSlot("PENDING"); row.setCreatedAt(clock.instant());
        populate(row, body, actor);
        requests.saveAndFlush(row); audit(row, "SUBMITTED", actor);
        var result = response(row);
        operations.save(actor.getName(), "CREATE_ATTENDANCE_CORRECTION", key, body, result);
        return result;
    }

    @Transactional
    public Response update(UUID id, Write body, String match, JwtAuthenticationToken actor) {
        state.lockState();
        var row = read(id, actor); ownPending(row, actor); requireVersion(match, row.getVersion());
        Instant updated = nextUpdate(row);
        populate(row, body, actor); row.setUpdatedAt(updated);
        requests.flush(); audit(row, "UPDATED", actor);
        return response(row);
    }

    @Transactional
    public Response cancel(UUID id, String match, JwtAuthenticationToken actor) {
        state.lockState();
        var row = read(id, actor); ownPending(row, actor); requireVersion(match, row.getVersion());
        row.setStatus(CANCELLED); row.setActiveSlot(null); row.setUpdatedAt(nextUpdate(row));
        requests.flush(); audit(row, "CANCELLED", actor);
        return response(row);
    }

    @Transactional
    public Response decide(UUID id, Decision decision, String match, JwtAuthenticationToken actor) {
        requireReviewer(actor); state.lockState();
        var row = read(id, actor); requireVersion(match, row.getVersion());
        if (row.getEmployeeId().equals(employee(actor)) || row.getRequesterUserId().equals(actor.getName()))
            throw error(HttpStatus.FORBIDDEN, "You cannot review your own attendance correction");
        if (row.getStatus() != PENDING) throw error(HttpStatus.CONFLICT, "Only pending corrections can be reviewed");
        if (decision.status() != APPROVED && decision.status() != REJECTED)
            throw error(HttpStatus.BAD_REQUEST, "Decision must be APPROVED or REJECTED");
        String note = decision.reviewNote() == null ? null : decision.reviewNote().trim();
        if (decision.status() == REJECTED && (note == null || note.isBlank()))
            throw error(HttpStatus.BAD_REQUEST, "A rejection reason is required");
        if (decision.status() == APPROVED) {
            var body = new Write(row.getWorkDate(), row.getShiftRevision().getShiftId(),
                    row.getShiftRevision().getShiftVersion(), row.getBaseRecordVersion(),
                    row.getProposedCheckIn(), row.getProposedCheckOut(), row.getReason());
            var context = validate(row, body, actor);
            var day = context.day();
            if (day == null) {
                day = new AttendanceDay(); day.setId(UUID.randomUUID());
                day.setEmployeeId(row.getEmployeeId()); day.setWorkDate(row.getWorkDate());
                day.setShiftRevision(row.getShiftRevision()); day.setEmployeeSnapshot(snapshots.employee(context.person()));
            }
            // Use freshly verified coverage; retain original punch columns and events verbatim.
            day.setLeaveSnapshot(snapshots.leaves(row.getEmployeeId(), row.getWorkDate(), context.leaves()));
            day.setHolidaySnapshot(snapshots.holidays(row.getWorkDate(), context.holidays()));
            day.setSourceObservedAt(clock.instant());
            day.setCorrectedCheckIn(row.getProposedCheckIn()); day.setCorrectedCheckOut(row.getProposedCheckOut());
            day.setCorrectionId(row.getId());
            days.saveAndFlush(day);
        }
        row.setStatus(decision.status()); row.setActiveSlot(null);
        row.recordReview(actor.getName(), clock.instant(), note); row.setUpdatedAt(nextUpdate(row));
        requests.flush(); audit(row, decision.status().name(), actor);
        return response(row);
    }

    private void populate(AttendanceCorrection row, Write body, JwtAuthenticationToken actor) {
        if (requests.existsByEmployeeIdAndWorkDateAndActiveSlotAndIdNot(row.getEmployeeId(), body.workDate(), "PENDING", row.getId()))
            throw error(HttpStatus.CONFLICT, "A pending correction already exists for this date");
        var context = validate(row, body, actor);
        row.setWorkDate(body.workDate()); row.setShiftRevision(context.revision());
        row.setBaseRecordVersion(body.recordVersion());
        row.setBeforeCheckIn(context.day() == null ? null : context.day().getEffectiveCheckIn());
        row.setBeforeCheckOut(context.day() == null ? null : context.day().getEffectiveCheckOut());
        row.setEmployeeCode(context.person().employeeCode());
        row.setEmployeeName(context.person().firstName() + " " + context.person().lastName());
        row.setProposedCheckIn(body.proposedCheckIn()); row.setProposedCheckOut(body.proposedCheckOut());
        row.setReason(body.reason().trim()); row.setUpdatedAt(clock.instant());
    }

    private Context validate(AttendanceCorrection row, Write body, JwtAuthenticationToken actor) {
        if (body.workDate().isAfter(LocalDate.now(clock)))
            throw error(HttpStatus.BAD_REQUEST, "Correction date cannot be in the future");
        var day = days.findByEmployeeIdAndWorkDate(row.getEmployeeId(), body.workDate()).orElse(null);
        if (!Objects.equals(body.recordVersion(), day == null ? null : day.getVersion()))
            throw error(HttpStatus.PRECONDITION_FAILED, "Attendance changed; update the correction before approval");
        var revision = day == null ? schedules.effective(row.getEmployeeId(), body.workDate())
                .orElseThrow(() -> error(HttpStatus.CONFLICT, "No assigned schedule for correction date")).getShiftRevision()
                : day.getShiftRevision();
        if (!revision.getShiftId().equals(body.shiftId()) || revision.getShiftVersion() != body.shiftVersion())
            throw error(HttpStatus.PRECONDITION_FAILED, "Assigned schedule changed; update the correction");
        var shift = shifts.definition(revision.getDefinition());
        Instant shiftEnd = shift.intervals().stream().map(i -> ShiftTimes.at(body.workDate(), shift, i.end()))
                .max(Comparator.naturalOrder()).orElseThrow();
        if (clock.instant().isBefore(shiftEnd))
            throw error(HttpStatus.CONFLICT, "Corrections can only be submitted after the shift ends");
        Instant in = body.proposedCheckIn(), out = body.proposedCheckOut();
        if (!out.isAfter(in) || out.isAfter(clock.instant()) || in.getNano() != 0 || out.getNano() != 0
                || in.isBefore(ShiftTimes.windowStart(body.workDate(), shift))
                || out.isAfter(ShiftTimes.windowEnd(body.workDate(), shift)))
            throw error(HttpStatus.BAD_REQUEST, "Corrected times must be ordered, not in the future and inside the recording window (whole seconds)");
        if (day != null && in.equals(day.getEffectiveCheckIn()) && out.equals(day.getEffectiveCheckOut()))
            throw error(HttpStatus.CONFLICT, "Corrected times are unchanged");
        var person = source.employee(row.getEmployeeId(), actor);
        if (!source.eligible(person, body.workDate()))
            throw error(HttpStatus.CONFLICT, "Employee is not eligible on the correction date");
        var leaves = source.leaves(row.getEmployeeId(), body.workDate(), body.workDate(), actor);
        var holidays = source.holidays(body.workDate(), body.workDate(), actor);
        var result = calculator.calculate(body.workDate(), shift, in, out, leaves, holidays, clock.instant());
        if (!"CLOSED".equals(result.status()))
            throw error(HttpStatus.CONFLICT, "Corrected times conflict with leave or company holidays");
        return new Context(day, revision, person, leaves, holidays);
    }

    private record Context(AttendanceDay day, ShiftRevision revision, WorkforceClient.Employee person,
            List<WorkforceClient.Leave> leaves, List<WorkforceClient.Holiday> holidays) {}

    private AttendanceCorrection read(UUID id, JwtAuthenticationToken actor) {
        var row = requests.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Attendance correction not found"));
        if (!row.getEmployeeId().equals(employee(actor)) && !reviewer(actor))
            throw error(HttpStatus.NOT_FOUND, "Attendance correction not found");
        return row;
    }
    private void ownPending(AttendanceCorrection row, JwtAuthenticationToken actor) {
        if (!row.getEmployeeId().equals(employee(actor))) throw error(HttpStatus.FORBIDDEN, "Only the owner may edit or cancel a correction");
        if (row.getStatus() != PENDING) throw error(HttpStatus.CONFLICT, "Only pending corrections can be edited or cancelled");
    }
    private Instant nextUpdate(AttendanceCorrection row) {
        return clock.instant().isAfter(row.getUpdatedAt()) ? clock.instant() : row.getUpdatedAt().plusNanos(1000);
    }
    private Response response(AttendanceCorrection row) {
        return new Response(row.getId(), row.getVersion(), row.getEmployeeId(), row.getEmployeeCode(), row.getEmployeeName(),
                row.getWorkDate(), row.getShiftRevision().getShiftId(), row.getShiftRevision().getShiftVersion(),
                shifts.definition(row.getShiftRevision().getDefinition()), row.getBaseRecordVersion(),
                row.getBeforeCheckIn(), row.getBeforeCheckOut(), row.getProposedCheckIn(), row.getProposedCheckOut(),
                row.getReason(), row.getStatus(), row.getReviewNote(), row.getCreatedAt(), row.getUpdatedAt(),
                row.getReviewedBy(), row.getReviewedAt());
    }
    private void audit(AttendanceCorrection row, String action, JwtAuthenticationToken actor) {
        var event = new AttendanceCorrectionHistory(); event.recordAudit(actor.getName(), clock.instant());
        event.setRequestId(row.getId()); event.setRequestVersion(row.getVersion()); event.setAction(action);
        event.setSnapshot(mapper.writeValueAsString(response(row))); history.save(event);
    }
}
