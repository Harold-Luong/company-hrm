package com.company.attendance.service;

import com.company.attendance.dto.*;
import com.company.attendance.entity.*;
import com.company.attendance.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static com.company.attendance.service.ApiRules.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttendanceService {
    private final AttendanceDayRepository days;
    private final AttendanceEventRepository events;
    private final ScheduleStateRepository state;
    private final ScheduleService schedules;
    private final ShiftService shifts;
    private final WorkforceClient source;
    private final AttendanceCalculator calculator;
    private final AttendancePermissionService permissions;
    private final OvertimeService overtime;
    private final OperationService operations;
    private final ObjectMapper mapper;
    private final AttendanceSnapshots snapshots;
    private final Clock clock;

    @Transactional
    public AttendanceResponse punch(boolean checkIn, String key, String ip, JwtAuthenticationToken actor) {
        state.lockState();
        String type = checkIn ? "CHECK_IN" : "CHECK_OUT";
        // Payload is empty; replay is independent of the current date and returns the
        // original response.
        AttendanceResponse replay = operations.replay(actor.getName(), type, key, Map.of(), AttendanceResponse.class);
        if (replay != null)
            return replay;
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        LocalDate date = LocalDate.now(clock);
        UUID employee = employee(actor);
        var previous = days.findByEmployeeIdAndWorkDate(employee, date.minusDays(1));
        if (previous.isPresent() && previous.get().getCheckOut() == null
                && shifts.definition(previous.get().getShiftDefinition()).overnight()
                && !now.isAfter(ShiftTimes.windowEnd(previous.get().getWorkDate(),
                        shifts.definition(previous.get().getShiftDefinition())))) {
            if (checkIn)
                throw error(HttpStatus.CONFLICT, "Finish the previous overnight shift first");
            date = date.minusDays(1);
        } else if (checkIn && previous.isEmpty()) {
            var priorRule = schedules.effective(employee, date.minusDays(1));
            if (priorRule.isPresent()) {
                var plan = shifts.definition(priorRule.get().getDefinition());
                if (plan.overnight() && !now.isAfter(ShiftTimes.windowEnd(date.minusDays(1), plan)))
                    date = date.minusDays(1);
            }
        }
        var person = source.employee(employee, actor);
        if (!source.eligible(person, date))
            throw error(HttpStatus.FORBIDDEN, "Employee is not eligible to record attendance");
        var existing = days.findByEmployeeIdAndWorkDate(employee, date);
        AttendanceDay day;
        if (checkIn) {
            if (existing.isPresent())
                throw error(HttpStatus.CONFLICT, "Attendance has already been recorded for this day");
            var rule = schedules.effective(employee, date)
                    .orElseThrow(() -> error(HttpStatus.CONFLICT, "No assigned schedule for today"));
            day = new AttendanceDay();
            day.setId(UUID.randomUUID());
            day.setEmployeeId(employee);
            day.setWorkDate(date);
            day.setShiftRevision(rule.getShiftRevision());
            capture(day, person, actor);
            validateWindow(day, now);
            var planned = result(day);
            if (planned.remainingRequiredMinutes() == 0 || "SOURCE_CONFLICT".equals(planned.status()))
                throw error(HttpStatus.CONFLICT, "Today is a non-working day or leave coverage conflicts");
            day.setCheckIn(now);
            days.saveAndFlush(day);
        } else {
            day = existing.orElseThrow(() -> error(HttpStatus.CONFLICT, "Check-in is required first"));
            if (day.getCheckOut() != null)
                throw error(HttpStatus.CONFLICT, "Already checked out");
            validateWindow(day, now);
            if (!now.isAfter(day.getCheckIn()))
                throw error(HttpStatus.CONFLICT, "Check-out must be after check-in");
            day.setCheckOut(now);
            days.flush();
        }
        var event = new AttendanceEvent();
        event.recordAudit(actor.getName(), now);
        event.setDayId(day.getId());
        event.setEventType(type);
        event.setMethod("CORPORATE_NETWORK");
        event.setSourceIp(ip);
        events.save(event);
        var response = result(day);
        operations.save(actor.getName(), type, key, Map.of(), response);
        return response;
    }

    public List<AttendanceResponse> mine(LocalDate from, LocalDate until, JwtAuthenticationToken actor) {
        return report(employee(actor), null, from, until, actor);
    }

    public List<AttendanceResponse> report(UUID employee, UUID department, LocalDate from, LocalDate until,
            JwtAuthenticationToken actor) {
        ScheduleService.dateRange(from, until);
        if (employee == null || !employee.equals(employee(actor)))
            requireReviewer(actor);
        if (until.isAfter(LocalDate.now(clock)))
            throw error(HttpStatus.BAD_REQUEST, "Attendance reports cannot include future dates");
        List<WorkforceClient.Employee> people = employee == null ? source.employees(actor)
                : List.of(source.employee(employee, actor));
        if (department != null)
            people = people.stream().filter(p -> p.department() != null && department.equals(p.department().id()))
                    .toList();
        long rows = people.size() * (ChronoUnit.DAYS.between(from, until) + 1);
        if (rows > 5000)
            throw error(HttpStatus.UNPROCESSABLE_CONTENT,
                    "Report exceeds 5000 rows; narrow the date or employee scope");
        List<AttendanceResponse> result = new ArrayList<>();
        var holidays = source.holidays(from, until, actor);
        for (var person : people) {
            var leave = source.leaves(person.id(), from, until, actor);
            Map<LocalDate, AttendanceDay> saved = new HashMap<>();
            days.findByEmployeeIdAndWorkDateBetweenOrderByWorkDate(person.id(), from, until)
                    .forEach(d -> saved.put(d.getWorkDate(), d));
            var rules = schedules.effectiveRules(person.id(), from, until);
            var activeRequests = permissions.activeRequests(person.id(), from, until);
            var overtimeSummaries = overtime.summaries(person.id(), from, until);
            String employeeSnapshot = snapshots.employee(person);
            for (LocalDate date = from; !date.isAfter(until); date = date.plusDays(1)) {
                var requests = activeRequests.getOrDefault(date, List.of());
                var ot = overtimeSummaries.getOrDefault(date, OvertimeModels.Summary.empty());
                if (saved.containsKey(date)) {
                    result.add(result(saved.get(date), requests, ot));
                    continue;
                }
                if (date.isBefore(person.hireDate()))
                    continue;
                var rule = schedules.effective(person.id(), date, rules);
                if (rule.isEmpty()) {
                    result.add(noSchedule(person, date, ot));
                    continue;
                }
                AttendanceDay transientDay = new AttendanceDay();
                transientDay.setEmployeeId(person.id());
                transientDay.setWorkDate(date);
                transientDay.setShiftRevision(rule.get().getShiftRevision());
                transientDay.setEmployeeSnapshot(employeeSnapshot);
                transientDay.setLeaveSnapshot(snapshots.leaves(person.id(), date, leave));
                transientDay.setHolidaySnapshot(snapshots.holidays(date, holidays));
                transientDay.setSourceObservedAt(clock.instant());
                result.add(result(transientDay, requests, ot));
            }
        }
        return result;
    }

    @Transactional
    public AttendanceResponse refresh(UUID employee, LocalDate date, String match, JwtAuthenticationToken actor) {
        requireReviewer(actor);
        state.lockState();
        var day = days.findByEmployeeIdAndWorkDate(employee, date)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Recorded day not found"));
        requireVersion(match, day.getVersion());
        var before = Map.of("employeeId", employee, "date", date, "recordVersion", day.getVersion(),
                "leaveSnapshot", day.getLeaveSnapshot(), "holidaySnapshot", day.getHolidaySnapshot(),
                "sourceObservedAt", day.getSourceObservedAt());
        // Explicit refresh updates external coverage only; the original assigned shift
        // and events are preserved.
        day.setLeaveSnapshot(snapshots.leaves(employee, date, source.leaves(employee, date, date, actor)));
        day.setHolidaySnapshot(snapshots.holidays(date, source.holidays(date, date, actor)));
        day.setSourceObservedAt(clock.instant());
        days.flush();
        var response = result(day);
        operations.save(actor.getName(), "REFRESH_COVERAGE", UUID.randomUUID().toString(), before, response);
        return response;
    }

    public List<AttendanceEvent> history(UUID employee, LocalDate date, JwtAuthenticationToken actor) {
        if (!employee.equals(employee(actor)))
            requireReviewer(actor);
        var day = days.findByEmployeeIdAndWorkDate(employee, date)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Recorded day not found"));
        return events.findByDayIdOrderByOccurredAt(day.getId());
    }

    private void capture(AttendanceDay day, WorkforceClient.Employee person, JwtAuthenticationToken actor) {
        day.setEmployeeSnapshot(snapshots.employee(person));
        day.setLeaveSnapshot(
                snapshots.leaves(person.id(), day.getWorkDate(),
                        source.leaves(person.id(), day.getWorkDate(), day.getWorkDate(), actor)));
        day.setHolidaySnapshot(
                snapshots.holidays(day.getWorkDate(), source.holidays(day.getWorkDate(), day.getWorkDate(), actor)));
        day.setSourceObservedAt(clock.instant());
    }

    private void validateWindow(AttendanceDay day, Instant time) {
        var shift = shifts.definition(day.getShiftDefinition());
        if (time.isBefore(ShiftTimes.windowStart(day.getWorkDate(), shift))
                || time.isAfter(ShiftTimes.windowEnd(day.getWorkDate(), shift)))
            throw error(HttpStatus.FORBIDDEN, "Outside the assigned recording window");
    }

    private AttendanceResponse result(AttendanceDay day) {
        return result(day, permissions.activeRequests(day.getEmployeeId(), day.getWorkDate(), day.getWorkDate())
                .getOrDefault(day.getWorkDate(), List.of()),
                overtime.summary(day.getEmployeeId(), day.getWorkDate()));
    }

    private AttendanceResponse result(AttendanceDay day, List<AttendanceRequest> requests, OvertimeModels.Summary ot) {
        var employee = mapper.readValue(day.getEmployeeSnapshot(), WorkforceClient.Employee.class);
        var shift = shifts.definition(day.getShiftDefinition());
        var leaves = List.of(mapper.readValue(day.getLeaveSnapshot(), WorkforceClient.Leave[].class));
        var holidays = List.of(mapper.readValue(day.getHolidaySnapshot(), WorkforceClient.Holiday[].class));
        var result = calculator.calculate(day.getWorkDate(), shift,
                day.getCheckIn(), day.getCheckOut(), leaves, holidays, clock.instant());
        return new AttendanceResponse(employee.id(), employee.employeeCode(),
                employee.firstName() + " " + employee.lastName(),
                employee.department() == null ? null : employee.department().id(), day.getWorkDate(), day.getShiftId(),
                day.getShiftVersion(),
                day.getCheckIn(), day.getCheckOut(), result.base(), result.annual(), result.unpaid(),
                result.leaveDays(), result.remaining(),
                result.actualSeconds(), result.counted(), result.lateSeconds(), result.late(), result.earlySeconds(),
                result.early(),
                result.status(), result.pending(), result.applied(), day.getSourceObservedAt(),
                day.getId() == null ? null : day.getVersion(), "DRAFT",
                permissions.coverage(day.getWorkDate(), day.getShiftId(), day.getShiftVersion(),
                        shift, day.getCheckIn(), day.getCheckOut(), result, leaves, holidays, requests),
                ot);
    }

    private AttendanceResponse noSchedule(WorkforceClient.Employee person, LocalDate date, OvertimeModels.Summary ot) {
        return new AttendanceResponse(person.id(), person.employeeCode(), person.firstName() + " " + person.lastName(),
                person.department() == null ? null : person.department().id(), date, null, null, null, null,
                0, 0, 0, BigDecimal.ZERO, 0, null, null, null, null, null, null,
                "NO_SCHEDULE", List.of(), List.of(), clock.instant(), null, "DRAFT",
                AttendancePermissionCoverage.empty(), ot);
    }
}
