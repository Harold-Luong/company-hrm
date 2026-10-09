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
    private final AttendanceRequestRepository requests;
    private final OvertimeRequestRepository overtime;
    private final tools.jackson.databind.ObjectMapper mapper;
    private final Clock clock;

    /**
     * Previews a schedule request.
     *
     * @param request the schedule request
     * @param actor   the authenticated user
     * @return the preview of the schedule
     */
    @Transactional
    public Preview preview(ScheduleRequest request, JwtAuthenticationToken actor) {
        requireReviewer(actor);
        var lock = state.lockState();
        var shift = validate(request, actor);
        return preview(request, shift, lock.getRevision(), actor);
    }

    /**
     * Applies a schedule request.
     * 
     * @param request
     * @param shift
     * @param revision
     * @param actor
     * @return
     */
    private Preview preview(ScheduleRequest request, ShiftResponse shift, long revision, JwtAuthenticationToken actor) {
        var affected = affected(request);
        var conflicts = days.findByWorkDateBetween(request.from(), until(request)).stream()
                .filter(d -> request.weekdays().contains(d.getWorkDate().getDayOfWeek()))
                .filter(d -> request.scope() != ScheduleScope.SELECTED_EMPLOYEES
                        || request.employeeIds().contains(d.getEmployeeId()))
                .filter(d -> request.scope() != ScheduleScope.COMPANY_DEFAULT
                        || effective(d.getEmployeeId(), d.getWorkDate()).map(r -> r.getEmployeeId() == null)
                                .orElse(true))
                .count();
        // Block changes under approved leave rather than silently changing its time
        // coverage.
        List<WorkforceClient.Leave> leaves = new ArrayList<>();
        if (request.scope() == ScheduleScope.SELECTED_EMPLOYEES) {
            for (UUID id : request.employeeIds())
                leaves.addAll(source.leaves(id, request.from(), until(request), actor));
        } else
            leaves.addAll(source.leaves(null, request.from(), until(request), actor));
        int approved = (int) leaves.stream().filter(l -> "APPROVED".equals(l.status()) && affectsLeave(request, l))
                .count();
        int approvedRequests = (int) requests.findByStatusAndWorkDateBetween(
                com.company.attendance.enums.AttendanceRequestStatus.APPROVED, request.from(), until(request)).stream()
                .filter(r -> request.weekdays().contains(r.getWorkDate().getDayOfWeek()))
                .filter(r -> request.scope() != ScheduleScope.SELECTED_EMPLOYEES
                        || request.employeeIds().contains(r.getEmployeeId()))
                .filter(r -> request.scope() != ScheduleScope.COMPANY_DEFAULT
                        || effective(r.getEmployeeId(), r.getWorkDate()).map(rule -> rule.getEmployeeId() == null)
                                .orElse(true))
                .count();
        return new Preview(revision, shift, affected.size(), affected.stream().map(ScheduleRule::getEmployeeId)
                .filter(Objects::nonNull).distinct().sorted().toList(), (int) conflicts, approved, approvedRequests,
                overtimeConflicts(request, shift.definition()));
    }

    @Transactional
    public Applied apply(ScheduleRequest request, String match, String key, JwtAuthenticationToken actor) {
        requireReviewer(actor);
        var lock = state.lockState();
        Applied replay = operations.replay(actor.getName(), "APPLY_SCHEDULE", key, request, Applied.class);
        if (replay != null)
            return replay;
        requireVersion(match, lock.getRevision());
        var shift = validate(request, actor);
        var preview = preview(request, shift, lock.getRevision(), actor);
        if (preview.approvedRequestConflicts() > 0)
            throw error(HttpStatus.CONFLICT, "Schedule affects approved attendance requests; resolve conflicts first");
        if (preview.recordedDayConflicts() > 0 || preview.approvedLeaveConflicts() > 0)
            throw error(HttpStatus.CONFLICT,
                    "Schedule affects recorded attendance or approved leave; resolve conflicts first");
        if (preview.approvedOvertimeConflicts() > 0)
            throw error(HttpStatus.CONFLICT, "Schedule affects approved OT; choose another date range");
        var affected = affected(request);
        var history = new ScheduleBatch();
        history.recordAudit(actor.getName(), clock.instant());
        UUID batch = history.getId();
        history.setScheduleRevision(lock.getRevision() + 1);
        history.setRequestBody(mapper.writeValueAsString(request));
        history.setReplacedRules(mapper.writeValueAsString(affected));
        batches.saveAndFlush(history);
        for (var old : affected) {
            LocalDate from = old.getEffectiveFrom(), until = old.getEffectiveUntil();
            if (from.isBefore(request.from()))
                rules.save(copy(old, from, request.from().minusDays(1)));
            if (until.isAfter(until(request)))
                rules.save(copy(old, until(request).plusDays(1), until));
            rules.delete(old);
        }
        List<UUID> owners = request.scope() == ScheduleScope.SELECTED_EMPLOYEES
                ? new ArrayList<>(request.employeeIds())
                : Collections.singletonList(null);
        int created = 0;
        var revision = shifts.revision(request.shiftId(), request.shiftVersion());
        for (UUID owner : owners)
            for (DayOfWeek weekday : request.weekdays()) {
                var rule = new ScheduleRule();
                rule.setId(UUID.randomUUID());
                rule.setBatchId(batch);
                rule.setEmployeeId(owner);
                rule.setShiftRevision(revision);
                rule.setWeekday(weekday.getValue());
                rule.setEffectiveFrom(request.from());
                rule.setEffectiveUntil(until(request));
                rules.save(rule);
                created++;
            }
        validateChangedSchedule(request);
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
        if (!employee.equals(employee(actor)))
            requireReviewer(actor);
        dateRange(from, until);
        source.employee(employee, actor);
        Map<LocalDate, AttendanceDay> recordedDays = new HashMap<>();
        days.findByEmployeeIdAndWorkDateBetweenOrderByWorkDate(employee, from, until)
                .forEach(day -> recordedDays.put(day.getWorkDate(), day));
        var candidates = effectiveRules(employee, from, until);
        List<Day> result = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(until); date = date.plusDays(1)) {
            var d = recordedDays.get(date);
            if (d != null) {
                var definition = shifts.definition(d.getShiftDefinition());
                result.add(new Day(employee, date, d.getShiftId(), d.getShiftVersion(), definition,
                        shifts.validate(definition), "RECORDED_SNAPSHOT"));
            } else {
                var rule = effective(employee, date, candidates);
                result.add(rule.isEmpty() ? new Day(employee, date, null, null, null, 0, "NO_SCHEDULE")
                        : new Day(employee, date, rule.get().getShiftId(), rule.get().getShiftVersion(),
                                shifts.definition(rule.get().getDefinition()),
                                shifts.validate(shifts.definition(rule.get().getDefinition())),
                                rule.get().getEmployeeId() == null ? "COMPANY_DEFAULT" : "EMPLOYEE_OVERRIDE"));
            }
        }
        return result;
    }

    public Optional<ScheduleRule> effective(UUID employee, LocalDate date) {
        return effective(employee, date, rules.effective(employee, date, date.getDayOfWeek().getValue()));
    }

    public List<ScheduleRule> effectiveRules(UUID employee, LocalDate from, LocalDate until) {
        return rules.applicable(employee, from, until);
    }

    /**
     * Resolve the same personal-over-company precedence for single-day and range
     * reads.
     */
    public Optional<ScheduleRule> effective(UUID employee, LocalDate date, List<ScheduleRule> candidates) {
        var found = candidates.stream()
                .filter(r -> r.getEmployeeId() == null || r.getEmployeeId().equals(employee))
                .filter(r -> r.getWeekday() == date.getDayOfWeek().getValue())
                .filter(r -> !r.getEffectiveFrom().isAfter(date) && !r.getEffectiveUntil().isBefore(date))
                .toList();
        var personal = found.stream().filter(r -> r.getEmployeeId() != null).toList();
        var selected = personal.isEmpty() ? found : personal;
        if (selected.size() > 1)
            throw error(HttpStatus.CONFLICT, "Overlapping schedule rules require correction");
        return selected.stream().findFirst();
    }

    public static void dateRange(LocalDate from, LocalDate until) {
        if (from == null || until == null || until.isBefore(from) || until.isAfter(from.plusDays(365)))
            throw error(HttpStatus.BAD_REQUEST, "Date range must be ordered and at most 366 days");
    }

    private ShiftResponse validate(ScheduleRequest request, JwtAuthenticationToken actor) {
        if (request.from().isBefore(LocalDate.now(clock)) || until(request).isBefore(request.from()))
            throw error(HttpStatus.BAD_REQUEST,
                    "Schedule changes must start today or later, with an ordered date range");
        if ((request.scope() == ScheduleScope.SELECTED_EMPLOYEES) != !request.employeeIds().isEmpty())
            throw error(HttpStatus.BAD_REQUEST, "Employee IDs are required only for SELECTED_EMPLOYEES");
        var shift = shifts.get(request.shiftId());
        if (!shift.active())
            throw error(HttpStatus.CONFLICT, "Inactive shift cannot be assigned");
        if (shift.version() != request.shiftVersion())
            throw error(HttpStatus.PRECONDITION_FAILED, "Shift changed; reload preview");
        for (UUID employee : request.employeeIds()) {
            var person = source.employee(employee, actor);
            if (!source.eligible(person, request.from()))
                throw error(HttpStatus.CONFLICT, "Selected employee is not eligible on the start date");
        }
        return shift;
    }

    private List<ScheduleRule> affected(ScheduleRequest request) {
        return rules.overlapping(request.from(), until(request)).stream()
                .filter(r -> matches(request, r.getEmployeeId(), r.getWeekday())).toList();
    }

    private int overtimeConflicts(ScheduleRequest request, ShiftRequest plan) {
        int count = 0;
        for (var ot : overtime.findByStatusAndWorkDateBetween("APPROVED", request.from().minusDays(1),
                until(request).plusDays(1))) {
            if (request.scope() == ScheduleScope.SELECTED_EMPLOYEES
                    && !request.employeeIds().contains(ot.getEmployeeId()))
                continue;
            for (LocalDate date = ot.getWorkDate().minusDays(1); !date
                    .isAfter(ot.getEndTime().toLocalDate()); date = date.plusDays(1)) {
                if (date.isBefore(request.from()) || date.isAfter(until(request))
                        || !request.weekdays().contains(date.getDayOfWeek()))
                    continue;
                var existing = effective(ot.getEmployeeId(), date);
                if (request.scope() == ScheduleScope.COMPANY_DEFAULT
                        && existing.map(r -> r.getEmployeeId() != null).orElse(false))
                    continue;
                if (date.isBefore(ot.getWorkDate()) && !plan.overnight()
                        && !existing.map(r -> shifts.definition(r.getDefinition()).overnight()).orElse(false))
                    continue;
                count++;
                break;
            }
        }
        return count;
    }

    private void validateChangedSchedule(ScheduleRequest request) {
        var relevant = rules.overlapping(request.from().minusDays(1), until(request).plusDays(1));
        Set<UUID> employees = new HashSet<>(request.employeeIds());
        if (request.scope() != ScheduleScope.SELECTED_EMPLOYEES) {
            // The zero UUID checks the company fallback without any personal override.
            employees.add(new UUID(0, 0));
            relevant.stream().map(ScheduleRule::getEmployeeId).filter(Objects::nonNull).forEach(employees::add);
        }
        // Schedules are weekly and only change at effective-range boundaries. Check a
        // full week at every boundary rather than expanding an unbounded default.
        Set<LocalDate> boundaries = new HashSet<>(Set.of(request.from()));
        boundaries.add(until(request));
        for (var rule : relevant) {
            if (!rule.getEffectiveFrom().isBefore(request.from()))
                boundaries.add(rule.getEffectiveFrom());
            if (!rule.getEffectiveUntil().isAfter(until(request)))
                boundaries.add(rule.getEffectiveUntil());
        }
        for (UUID employee : employees)
            for (LocalDate date : boundaries)
                validateAdjacent(employee, date, date.plusDays(7));
    }

    /** Kiểm tra ca vừa phân công không chồng giờ với ca của ngày liền kề. */
    private void validateAdjacent(UUID employee, LocalDate from, LocalDate until) {
        for (LocalDate date = from.minusDays(1); !date.isAfter(until); date = date.plusDays(1)) {
            var a = effective(employee, date);
            var b = effective(employee, date.plusDays(1));
            if (a.isEmpty() || b.isEmpty())
                continue;
            var first = shifts.definition(a.get().getDefinition());
            var second = shifts.definition(b.get().getDefinition());
            if (ShiftTimes.at(date, first, first.intervals().getLast().end())
                    .isAfter(ShiftTimes.at(date.plusDays(1), second, second.intervals().getFirst().start())))
                throw error(HttpStatus.CONFLICT, "Schedule overlaps an adjacent assigned shift");
        }
    }

    private boolean affectsLeave(ScheduleRequest request, WorkforceClient.Leave leave) {
        LocalDate first = leave.startDate().isAfter(request.from()) ? leave.startDate() : request.from();
        LocalDate last = leave.endDate().isBefore(until(request)) ? leave.endDate() : until(request);
        for (LocalDate date = first; !date.isAfter(last); date = date.plusDays(1)) {
            if (!request.weekdays().contains(date.getDayOfWeek()))
                continue;
            if (request.scope() != ScheduleScope.COMPANY_DEFAULT
                    || effective(leave.employeeId(), date).map(r -> r.getEmployeeId() == null).orElse(true))
                return true;
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

    private LocalDate until(ScheduleRequest request) {
        return request.until() == null ? LAST_DATE : request.until();
    }

    private ScheduleRule copy(ScheduleRule original, LocalDate from, LocalDate until) {
        var r = new ScheduleRule();
        r.setId(UUID.randomUUID());
        r.setEmployeeId(original.getEmployeeId());
        r.setBatchId(original.getBatchId());
        r.setShiftRevision(original.getShiftRevision());
        r.setWeekday(original.getWeekday());
        r.setEffectiveFrom(from);
        r.setEffectiveUntil(until);
        return r;
    }
}
