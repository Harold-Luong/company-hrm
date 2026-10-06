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
    private final OperationService operations;
    private final ObjectMapper mapper;
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
            day.setShiftId(rule.getShiftId());
            day.setShiftVersion(rule.getShiftVersion());
            day.setShiftDefinition(rule.getDefinition());
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
        event.setId(UUID.randomUUID());
        event.setDayId(day.getId());
        event.setEventType(type);
        event.setEventAt(now);
        event.setMethod("CORPORATE_NETWORK");
        event.setActorUserId(actor.getName());
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
            throw error(HttpStatus.UNPROCESSABLE_CONTENT, "Report exceeds 5000 rows; narrow the date or employee scope");
        List<AttendanceResponse> result = new ArrayList<>();
        var holidays = source.holidays(from, until, actor);
        for (var person : people) {
            var leave = source.leaves(person.id(), from, until, actor);
            Map<LocalDate, AttendanceDay> saved = new HashMap<>();
            days.findByEmployeeIdAndWorkDateBetweenOrderByWorkDate(person.id(), from, until)
                    .forEach(d -> saved.put(d.getWorkDate(), d));
            for (LocalDate date = from; !date.isAfter(until); date = date.plusDays(1)) {
                if (saved.containsKey(date)) {
                    result.add(result(saved.get(date)));
                    continue;
                }
                if (date.isBefore(person.hireDate()))
                    continue;
                var rule = schedules.effective(person.id(), date);
                if (rule.isEmpty()) {
                    result.add(noSchedule(person, date));
                    continue;
                }
                AttendanceDay transientDay = new AttendanceDay();
                transientDay.setEmployeeId(person.id());
                transientDay.setWorkDate(date);
                transientDay.setShiftId(rule.get().getShiftId());
                transientDay.setShiftVersion(rule.get().getShiftVersion());
                transientDay.setShiftDefinition(rule.get().getDefinition());
                transientDay.setEmployeeSnapshot(mapper.writeValueAsString(person));
                transientDay.setLeaveSnapshot(mapper.writeValueAsString(leave));
                transientDay.setHolidaySnapshot(mapper.writeValueAsString(holidays));
                transientDay.setSourceObservedAt(clock.instant());
                result.add(result(transientDay));
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
        day.setLeaveSnapshot(mapper.writeValueAsString(source.leaves(employee, date, date, actor)));
        day.setHolidaySnapshot(mapper.writeValueAsString(source.holidays(date, date, actor)));
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
        return events.findByDayIdOrderByEventAt(day.getId());
    }

    private void capture(AttendanceDay day, WorkforceClient.Employee person, JwtAuthenticationToken actor) {
        day.setEmployeeSnapshot(mapper.writeValueAsString(person));
        day.setLeaveSnapshot(
                mapper.writeValueAsString(source.leaves(person.id(), day.getWorkDate(), day.getWorkDate(), actor)));
        day.setHolidaySnapshot(mapper.writeValueAsString(source.holidays(day.getWorkDate(), day.getWorkDate(), actor)));
        day.setSourceObservedAt(clock.instant());
    }

    private void validateWindow(AttendanceDay day, Instant time) {
        var shift = shifts.definition(day.getShiftDefinition());
        LocalTime local = time.atZone(ZoneId.of(shift.timezone())).toLocalTime();
        if (local.isBefore(shift.checkInFrom()) || local.isAfter(shift.checkOutUntil()))
            throw error(HttpStatus.FORBIDDEN, "Outside the assigned recording window");
    }

    private AttendanceResponse result(AttendanceDay day) {
        var employee = mapper.readValue(day.getEmployeeSnapshot(), WorkforceClient.Employee.class);
        var result = calculator.calculate(day.getWorkDate(), shifts.definition(day.getShiftDefinition()),
                day.getCheckIn(), day.getCheckOut(),
                List.of(mapper.readValue(day.getLeaveSnapshot(), WorkforceClient.Leave[].class)),
                List.of(mapper.readValue(day.getHolidaySnapshot(), WorkforceClient.Holiday[].class)), clock.instant());
        return new AttendanceResponse(employee.id(), employee.employeeCode(),
                employee.firstName() + " " + employee.lastName(),
                employee.department() == null ? null : employee.department().id(), day.getWorkDate(), day.getShiftId(),
                day.getShiftVersion(),
                day.getCheckIn(), day.getCheckOut(), result.base(), result.annual(), result.unpaid(),
                result.leaveDays(), result.remaining(),
                result.actualSeconds(), result.counted(), result.lateSeconds(), result.late(), result.earlySeconds(),
                result.early(),
                result.status(), result.pending(), result.applied(), day.getSourceObservedAt(),
                day.getId() == null ? null : day.getVersion(), "DRAFT");
    }

    private AttendanceResponse noSchedule(WorkforceClient.Employee person, LocalDate date) {
        return new AttendanceResponse(person.id(), person.employeeCode(), person.firstName() + " " + person.lastName(),
                person.department() == null ? null : person.department().id(), date, null, null, null, null,
                0, 0, 0, BigDecimal.ZERO, 0, null, null, null, null, null, null,
                "NO_SCHEDULE", List.of(), List.of(), clock.instant(), null, "DRAFT");
    }
}
