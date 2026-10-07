package com.company.attendance.service;

import com.company.attendance.dto.*;
import com.company.attendance.dto.ScheduleResponse.*;
import com.company.attendance.entity.*;
import com.company.attendance.enums.ScheduleScope;
import com.company.attendance.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import static com.company.attendance.service.ApiRules.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleService {
    private static final LocalDate LAST_DATE = LocalDate.of(9999, 12, 31);
    private final ScheduleRuleRepository rules;
    private final ScheduleStateRepository state;
    private final AttendanceDayRepository days;
    private final ShiftService shifts;
    private final WorkforceClient source;
    private final OperationService operations;
    private final ScheduleBatchRepository batches;
    private final tools.jackson.databind.ObjectMapper mapper;
    private final Clock clock;

    @Transactional
    public Preview preview(ScheduleRequest request, JwtAuthenticationToken actor) {
        requireReviewer(actor); var lock = state.lockState();
        var shift = validate(request, actor); return preview(request, shift, lock.getRevision(), actor);
    }
    private Preview preview(ScheduleRequest request, ShiftResponse shift, long revision, JwtAuthenticationToken actor) {
        var affected = affected(request);
        var conflicts = days.findByWorkDateBetween(request.from(), until(request)).stream()
                .filter(d -> request.weekdays().contains(d.getWorkDate().getDayOfWeek()))
                .filter(d -> request.scope() != ScheduleScope.SELECTED_EMPLOYEES || request.employeeIds().contains(d.getEmployeeId()))
                .filter(d -> request.scope() != ScheduleScope.COMPANY_DEFAULT
                        || effective(d.getEmployeeId(), d.getWorkDate()).map(r -> r.getEmployeeId() == null).orElse(true)).count();
        // Block changes under approved leave rather than silently changing its time coverage.
        List<WorkforceClient.Leave> leaves = new ArrayList<>();
        if (request.scope() == ScheduleScope.SELECTED_EMPLOYEES) {
            for (UUID id : request.employeeIds()) leaves.addAll(source.leaves(id, request.from(), until(request), actor));
        } else leaves.addAll(source.leaves(null, request.from(), until(request), actor));
        int approved = (int) leaves.stream().filter(l -> "APPROVED".equals(l.status()) && affectsLeave(request, l)).count();
        return new Preview(revision, shift, affected.size(), affected.stream().map(ScheduleRule::getEmployeeId)
                .filter(Objects::nonNull).distinct().sorted().toList(), (int) conflicts, approved);
    }
    @Transactional
    public Applied apply(ScheduleRequest request, String match, String key, JwtAuthenticationToken actor) {
        requireReviewer(actor); var lock = state.lockState();
        Applied replay = operations.replay(actor.getName(), "APPLY_SCHEDULE", key, request, Applied.class);
        if (replay != null) return replay;
        requireVersion(match, lock.getRevision());
        var shift = validate(request, actor);
        var preview = preview(request, shift, lock.getRevision(), actor);
        if (preview.recordedDayConflicts() > 0 || preview.approvedLeaveConflicts() > 0)
            throw error(HttpStatus.CONFLICT, "Schedule affects recorded attendance or approved leave; resolve conflicts first");
        UUID batch = UUID.randomUUID();
        var affected = affected(request);
        var history = new ScheduleBatch(); history.setId(batch); history.setScheduleRevision(lock.getRevision() + 1);
        history.setActorUserId(actor.getName()); history.setRequestBody(mapper.writeValueAsString(request));
        history.setReplacedRules(mapper.writeValueAsString(affected)); history.setOccurredAt(clock.instant());
        batches.saveAndFlush(history);
        for (var old : affected) {
            LocalDate from = old.getEffectiveFrom(), until = old.getEffectiveUntil();
            if (from.isBefore(request.from())) rules.save(copy(old, from, request.from().minusDays(1)));
            if (until.isAfter(until(request))) rules.save(copy(old, until(request).plusDays(1), until));
            rules.delete(old);
        }
        List<UUID> owners = request.scope() == ScheduleScope.SELECTED_EMPLOYEES
                ? new ArrayList<>(request.employeeIds()) : Collections.singletonList(null);
        int created = 0;
        var model = shifts.require(request.shiftId());
        for (UUID owner : owners) for (DayOfWeek weekday : request.weekdays()) {
            var rule = new ScheduleRule(); rule.setId(UUID.randomUUID()); rule.setBatchId(batch);
            rule.setEmployeeId(owner); rule.setShiftId(model.getId()); rule.setShiftVersion(model.getVersion());
            rule.setDefinition(model.getDefinition()); rule.setWeekday(weekday.getValue());
            rule.setEffectiveFrom(request.from()); rule.setEffectiveUntil(until(request)); rules.save(rule); created++;
        }
        lock.setRevision(lock.getRevision() + 1);
        var result = new Applied(batch, lock.getRevision(), affected.size(), created);
        operations.save(actor.getName(), "APPLY_SCHEDULE", key, request, result);
        return result;
    }
    public PageResponse<ScheduleBatch> history(int page, int size) {
        page(page, size);
        return PageResponse.from(batches.findAll(org.springframework.data.domain.PageRequest.of(page, size,
                org.springframework.data.domain.Sort.by("scheduleRevision").descending())));
    }
    public List<Day> schedule(UUID employee, LocalDate from, LocalDate until, JwtAuthenticationToken actor) {
        if (!employee.equals(employee(actor))) requireReviewer(actor);
        dateRange(from, until); source.employee(employee, actor);
        List<Day> result = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(until); date = date.plusDays(1)) {
            var recorded = days.findByEmployeeIdAndWorkDate(employee, date);
            if (recorded.isPresent()) {
                var d = recorded.get(); var definition = shifts.definition(d.getShiftDefinition());
                result.add(new Day(employee, date, d.getShiftId(), d.getShiftVersion(), definition, shifts.validate(definition), "RECORDED_SNAPSHOT"));
            } else {
                var rule = effective(employee, date);
                result.add(rule.isEmpty() ? new Day(employee, date, null, null, null, 0, "NO_SCHEDULE")
                    : new Day(employee, date, rule.get().getShiftId(), rule.get().getShiftVersion(),
                        shifts.definition(rule.get().getDefinition()), shifts.validate(shifts.definition(rule.get().getDefinition())),
                        rule.get().getEmployeeId() == null ? "COMPANY_DEFAULT" : "EMPLOYEE_OVERRIDE"));
            }
        }
        return result;
    }
    public Optional<ScheduleRule> effective(UUID employee, LocalDate date) {
        var found = rules.effective(employee, date, date.getDayOfWeek().getValue());
        var personal = found.stream().filter(r -> r.getEmployeeId() != null).toList();
        var selected = personal.isEmpty() ? found : personal;
        if (selected.size() > 1) throw error(HttpStatus.CONFLICT, "Overlapping schedule rules require correction");
        return selected.stream().findFirst();
    }
    public static void dateRange(LocalDate from, LocalDate until) {
        if (from == null || until == null || until.isBefore(from) || until.isAfter(from.plusDays(365)))
            throw error(HttpStatus.BAD_REQUEST, "Date range must be ordered and at most 366 days");
    }
    private ShiftResponse validate(ScheduleRequest request, JwtAuthenticationToken actor) {
        if (request.from().isBefore(LocalDate.now(clock)) || until(request).isBefore(request.from()))
            throw error(HttpStatus.BAD_REQUEST, "Schedule changes must start today or later, with an ordered date range");
        if ((request.scope() == ScheduleScope.SELECTED_EMPLOYEES) != !request.employeeIds().isEmpty())
            throw error(HttpStatus.BAD_REQUEST, "Employee IDs are required only for SELECTED_EMPLOYEES");
        var shift = shifts.get(request.shiftId());
        if (!shift.active()) throw error(HttpStatus.CONFLICT, "Inactive shift cannot be assigned");
        if (shift.version() != request.shiftVersion()) throw error(HttpStatus.PRECONDITION_FAILED, "Shift changed; reload preview");
        for (UUID employee : request.employeeIds()) {
            var person = source.employee(employee, actor);
            if (!source.eligible(person, request.from())) throw error(HttpStatus.CONFLICT, "Selected employee is not eligible on the start date");
        }
        return shift;
    }
    private List<ScheduleRule> affected(ScheduleRequest request) {
        return rules.overlapping(request.from(), until(request)).stream()
                .filter(r -> matches(request, r.getEmployeeId(), r.getWeekday())).toList();
    }
    private boolean affectsLeave(ScheduleRequest request, WorkforceClient.Leave leave) {
        LocalDate first = leave.startDate().isAfter(request.from()) ? leave.startDate() : request.from();
        LocalDate last = leave.endDate().isBefore(until(request)) ? leave.endDate() : until(request);
        for (LocalDate date = first; !date.isAfter(last); date = date.plusDays(1)) {
            if (!request.weekdays().contains(date.getDayOfWeek())) continue;
            if (request.scope() != ScheduleScope.COMPANY_DEFAULT
                    || effective(leave.employeeId(), date).map(r -> r.getEmployeeId() == null).orElse(true)) return true;
        }
        return false;
    }
    private boolean matches(ScheduleRequest request, UUID owner, int weekday) {
        return request.weekdays().contains(DayOfWeek.of(weekday)) && switch (request.scope()) {
            case ALL_EMPLOYEES -> true;
            case COMPANY_DEFAULT -> owner == null;
            case SELECTED_EMPLOYEES -> owner != null && request.employeeIds().contains(owner);
        };
    }
    private LocalDate until(ScheduleRequest request) { return request.until() == null ? LAST_DATE : request.until(); }
    private ScheduleRule copy(ScheduleRule original, LocalDate from, LocalDate until) {
        var r = new ScheduleRule(); r.setId(UUID.randomUUID()); r.setEmployeeId(original.getEmployeeId());
        r.setBatchId(original.getBatchId()); r.setShiftId(original.getShiftId()); r.setShiftVersion(original.getShiftVersion());
        r.setDefinition(original.getDefinition()); r.setWeekday(original.getWeekday()); r.setEffectiveFrom(from); r.setEffectiveUntil(until);
        return r;
    }
}
