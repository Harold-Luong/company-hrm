package com.company.attendance.service.worktime;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.company.attendance.service.worktime.WorkTimeInput.*;
import static com.company.attendance.service.worktime.WorkTimeResult.*;

/**
 * Internal Attendance calculation helpers. Inputs are explicit; no database,
 * network, system clock or Spring lifecycle is needed.
 */
public final class WorkTimeSupport {
    private WorkTimeSupport() {}

    public static WorkTimeResult calculate(WorkTimeInput input) {
        Objects.requireNonNull(input, "input");
        validate(input);
        int scheduled = input.schedule().stream().mapToInt(WorkInterval::minutes).sum();
        boolean holiday = input.calendarDay() == CalendarDay.PAID_HOLIDAY;
        int holidayMinutes = holiday ? scheduled : 0;
        int annual = 0, otherPaid = 0, unpaid = 0;
        List<WorkInterval> work = new ArrayList<>();
        List<String> pending = input.leaves().stream().filter(l -> l.status() == Approval.PENDING).map(Leave::id)
                .toList();
        Set<String> applied = new LinkedHashSet<>();
        List<String> issues = new ArrayList<>();
        // A holiday takes precedence over leave. No leave is charged on an unscheduled
        // day.
        if (!holiday)
            for (var interval : input.schedule()) {
                var covering = input.leaves().stream().filter(l -> l.status() == Approval.APPROVED
                        && (FULL_DAY.equals(l.period()) || interval.period().equals(l.period()))).toList();
                if (covering.size() > 1) {
                    issues.add("OVERLAPPING_LEAVE:" + interval.period());
                    continue;
                }
                if (covering.isEmpty()) {
                    work.add(interval);
                    continue;
                }
                var leave = covering.getFirst();
                applied.add(leave.id());
                switch (leave.type()) {
                    case ANNUAL_PAID -> annual += interval.minutes();
                    case OTHER_PAID -> otherPaid += interval.minutes();
                    case UNPAID -> unpaid += interval.minutes();
                }
                if (completePunch(input.attendance()) && overlapSeconds(interval.start(), interval.end(),
                        input.attendance().checkIn(), input.attendance().checkOut()) > 0)
                    issues.add("ATTENDANCE_DURING_LEAVE:" + leave.id());
            }
        if (!holiday && scheduled > 0)
            for (var leave : input.leaves()) {
                if (leave.status() == Approval.APPROVED && !FULL_DAY.equals(leave.period())
                        && input.schedule().stream().noneMatch(i -> i.period().equals(leave.period())))
                    issues.add("LEAVE_PERIOD_NOT_SCHEDULED:" + leave.id());
            }
        int required = work.stream().mapToInt(WorkInterval::minutes).sum();
        Work recorded;
        if (!issues.isEmpty())
            recorded = Work.unknown(Status.SOURCE_CONFLICT);
        else if (invalidPunch(input.attendance(), input.now()))
            recorded = Work.unknown(Status.INVALID_RECORD);
        else if (scheduled == 0 || holiday || required == 0) {
            if (input.attendance().checkIn() != null || input.attendance().checkOut() != null) {
                issues.add("REGULAR_ATTENDANCE_ON_NON_WORKING_DAY");
                recorded = Work.unknown(Status.SOURCE_CONFLICT);
            } else
                recorded = new Work(scheduled == 0 ? Status.NO_SCHEDULE : holiday ? Status.HOLIDAY : Status.ON_LEAVE,
                        0L, 0, 0L, 0, 0L, 0);
        } else if (!completePunch(input.attendance())) {
            boolean expired = input.now().isAfter(input.recordingClosesAt());
            recorded = Work.unknown(input.attendance().checkIn() == null
                    ? (expired ? Status.NO_RECORD : Status.NOT_STARTED)
                    : (expired ? Status.MISSING_CHECK_OUT : Status.OPEN));
        } else
            recorded = calculateWork(input, work, required);

        Integer payable = recorded.counted() == null ? null : recorded.counted() + annual + otherPaid + holidayMinutes;
        var policy = input.policy();
        var ot = calculateOvertime(input, holiday, issues);
        int otApproved = ot.stream().mapToInt(OvertimeResult::approvedMinutes).sum();
        Integer otCounted = ot.stream().anyMatch(r -> r.countedMinutes() == null) ? null
                : ot.stream().mapToInt(OvertimeResult::countedMinutes).sum();
        return new WorkTimeResult(input.workDate(), recorded.status(), scheduled, required, holidayMinutes,
                annual, otherPaid, unpaid, recorded.actual(), recorded.counted(), recorded.lateSeconds(),
                recorded.lateMinutes(), recorded.earlySeconds(), recorded.earlyMinutes(), payable,
                recorded.counted() == null ? null : policy.toDays(recorded.counted(), scheduled),
                recorded.status() == Status.SOURCE_CONFLICT ? null
                        : policy.toDays(annual + otherPaid + holidayMinutes, scheduled),
                payable == null ? null : policy.toDays(payable, scheduled), pending, new ArrayList<>(applied), issues,
                otApproved, otCounted, ot);
    }

    private static Work calculateWork(WorkTimeInput input, List<WorkInterval> intervals, int required) {
        Instant in = input.attendance().checkIn(), out = input.attendance().checkOut();
        long actual = 0, late = 0, early = 0;
        for (var interval : intervals) {
            actual += overlapSeconds(interval.start(), interval.end(), in, out);
            late += Math.max(0, Duration.between(interval.start(), min(in, interval.end())).getSeconds());
            early += Math.max(0, Duration.between(max(out, interval.start()), interval.end()).getSeconds());
        }
        int roundedLate = input.policy().roundDeviation(late);
        int roundedEarly = input.policy().roundDeviation(early);
        return new Work(Status.CLOSED, actual, Math.max(0, required - roundedLate - roundedEarly),
                late, roundedLate, early, roundedEarly);
    }

    private static List<OvertimeResult> calculateOvertime(WorkTimeInput input, boolean holiday, List<String> issues) {
        List<OvertimeResult> results = new ArrayList<>();
        var approved = input.overtime().stream().filter(o -> o.status() == Approval.APPROVED).toList();
        for (var ot : input.overtime()) {
            if (ot.status() != Approval.APPROVED) {
                results.add(new OvertimeResult(ot.id(), OvertimeStatus.NOT_APPROVED, 0, 0L, 0));
                continue;
            }
            boolean overlapsOther = approved.stream().anyMatch(other -> !other.id().equals(ot.id())
                    && overlapSeconds(ot.start(), ot.end(), other.start(), other.end()) > 0);
            boolean overlapsShift = !holiday && input.schedule().stream()
                    .anyMatch(i -> overlapSeconds(ot.start(), ot.end(), i.start(), i.end()) > 0);
            if (overlapsOther || overlapsShift) {
                issues.add("OVERTIME_OVERLAP:" + ot.id());
                results.add(new OvertimeResult(ot.id(), OvertimeStatus.SOURCE_CONFLICT, ot.minutes(), null, null));
            } else if (invalidPunch(ot.attendance(), input.now())) {
                results.add(new OvertimeResult(ot.id(), OvertimeStatus.INVALID_RECORD, ot.minutes(), null, null));
            } else if (!completePunch(ot.attendance())) {
                boolean expired = input.now().isAfter(ot.end());
                var status = ot.attendance().checkIn() == null
                        ? (expired ? OvertimeStatus.NO_RECORD : OvertimeStatus.NOT_STARTED)
                        : (expired ? OvertimeStatus.MISSING_CHECK_OUT : OvertimeStatus.OPEN);
                results.add(new OvertimeResult(ot.id(), status, ot.minutes(), null, null));
            } else {
                long seconds = overlapSeconds(ot.start(), ot.end(), ot.attendance().checkIn(),
                        ot.attendance().checkOut());
                results.add(new OvertimeResult(ot.id(), OvertimeStatus.CLOSED, ot.minutes(), seconds,
                        (int) (seconds / 60)));
            }
        }
        return results;
    }

    /**
     * One employee per call. Duplicate dates and mixed policies/timezones are
     * rejected.
     */
    public static WorkTimePeriodResult calculatePeriod(List<WorkTimeInput> days) {
        Objects.requireNonNull(days, "days");
        Set<LocalDate> dates = new HashSet<>();
        WorkTimePolicy policy = days.isEmpty() ? WorkTimePolicy.standard() : days.getFirst().policy();
        ZoneId zone = days.isEmpty() ? ZoneOffset.UTC : days.getFirst().zone();
        List<WorkTimeResult> results = new ArrayList<>();
        for (var day : days) {
            if (!dates.add(day.workDate()))
                throw new IllegalArgumentException("Duplicate work date: " + day.workDate());
            if (!policy.equals(day.policy()) || !zone.equals(day.zone()))
                throw new IllegalArgumentException("Period inputs must use the same policy and timezone");
            results.add(calculate(day));
        }
        validatePeriodRanges(days);
        results.sort(Comparator.comparing(WorkTimeResult::workDate));
        int known = results.stream().filter(r -> r.payableMinutes() != null).mapToInt(WorkTimeResult::payableMinutes)
                .reduce(0, Math::addExact);
        boolean regularComplete = results.stream().allMatch(r -> r.payableMinutes() != null);
        boolean otComplete = results.stream().allMatch(r -> r.countedOvertimeMinutes() != null);
        BigDecimal payableDays = null;
        if (regularComplete) {
            payableDays = policy.dayBasis() == WorkTimePolicy.DayBasis.STANDARD_DAY ? policy.toDays(known, 0)
                    : results.stream().map(WorkTimeResult::payableDays).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        Integer ot = otComplete
                ? results.stream().mapToInt(WorkTimeResult::countedOvertimeMinutes).reduce(0, Math::addExact)
                : null;
        return new WorkTimePeriodResult(results,
                results.stream().filter(r -> !r.complete()).map(WorkTimeResult::workDate).toList(),
                known, regularComplete ? known : null, payableDays, ot);
    }

    private static void validatePeriodRanges(List<WorkTimeInput> days) {
        List<PeriodRange> ranges = new ArrayList<>();
        Set<String> overtimeIds = new HashSet<>();
        for (var day : days) {
            for (var shift : day.schedule())
                ranges.add(new PeriodRange(day.workDate(), shift.start(), shift.end(), false,
                        day.calendarDay() == CalendarDay.PAID_HOLIDAY));
            for (var ot : day.overtime()) {
                if (!overtimeIds.add(ot.id())) throw new IllegalArgumentException("Duplicate OT id in period: " + ot.id());
                if (ot.status() == Approval.APPROVED)
                    ranges.add(new PeriodRange(day.workDate(), ot.start(), ot.end(), true, false));
            }
        }
        ranges.sort(Comparator.comparing(PeriodRange::start));
        for (int i = 0; i < ranges.size(); i++) {
            var first = ranges.get(i);
            for (int j = i + 1; j < ranges.size() && ranges.get(j).start().isBefore(first.end()); j++) {
                var second = ranges.get(j);
                if (first.date().equals(second.date())) continue; // reported by the daily calculation
                if ((first.overtime() && second.paidHoliday()) || (second.overtime() && first.paidHoliday())) continue;
                throw new IllegalArgumentException("Overlapping shift/OT across work dates: " + first.date() + " and " + second.date());
            }
        }
    }
    private record PeriodRange(LocalDate date, Instant start, Instant end, boolean overtime, boolean paidHoliday) {}

    private static void validate(WorkTimeInput input) {
        Set<String> periods = new HashSet<>(), leaveIds = new HashSet<>(), otIds = new HashSet<>();
        Instant previousEnd = null;
        for (var interval : input.schedule()) {
            if (!periods.add(interval.period()))
                throw new IllegalArgumentException("Duplicate work period: " + interval.period());
            if (previousEnd != null && interval.start().isBefore(previousEnd))
                throw new IllegalArgumentException("Work intervals must be ordered and non-overlapping");
            previousEnd = interval.end();
        }
        if (!input.schedule().isEmpty()) {
            var first = input.schedule().getFirst();
            var last = input.schedule().getLast();
            if (!first.start().atZone(input.zone()).toLocalDate().equals(input.workDate())
                    || Duration.between(first.start(), last.end()).compareTo(Duration.ofHours(24)) > 0)
                throw new IllegalArgumentException("Shift must start on workDate and span at most 24 hours");
            if (input.recordingClosesAt().isBefore(last.end()))
                throw new IllegalArgumentException("Recording cutoff must cover the complete shift");
        }
        for (var leave : input.leaves())
            if (!leaveIds.add(leave.id()))
                throw new IllegalArgumentException("Duplicate leave id: " + leave.id());
        for (var ot : input.overtime()) {
            if (!otIds.add(ot.id()))
                throw new IllegalArgumentException("Duplicate OT id: " + ot.id());
            if (!ot.start().atZone(input.zone()).toLocalDate().equals(input.workDate()))
                throw new IllegalArgumentException("OT must be assigned to its starting workDate");
        }
    }

    private static boolean invalidPunch(Punch punch, Instant now) {
        return (punch.checkOut() != null && (punch.checkIn() == null || !punch.checkOut().isAfter(punch.checkIn())
                || punch.checkOut().isAfter(now)))
                || (punch.checkIn() != null && punch.checkIn().isAfter(now));
    }

    private static boolean completePunch(Punch punch) {
        return punch.checkIn() != null && punch.checkOut() != null;
    }

    private static long overlapSeconds(Instant a, Instant b, Instant c, Instant d) {
        return Math.max(0, Duration.between(max(a, c), min(b, d)).getSeconds());
    }

    private static Instant min(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }

    private static Instant max(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }

    private record Work(Status status, Long actual, Integer counted, Long lateSeconds, Integer lateMinutes,
            Long earlySeconds, Integer earlyMinutes) {
        static Work unknown(Status status) {
            return new Work(status, null, null, null, null, null, null);
        }
    }
}
