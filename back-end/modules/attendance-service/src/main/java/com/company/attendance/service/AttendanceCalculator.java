package com.company.attendance.service;

import com.company.attendance.dto.ShiftRequest;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Component
public class AttendanceCalculator {
    public record Result(int base, int annual, int unpaid, BigDecimal leaveDays, int remaining,
            Long actualSeconds, Integer counted, Long lateSeconds, Integer late,
            Long earlySeconds, Integer early, String status,
            List<UUID> pending, List<UUID> applied) {
    }

    public Result calculate(LocalDate date, ShiftRequest shift, Instant in, Instant out,
            List<WorkforceClient.Leave> leaves, List<WorkforceClient.Holiday> holidays, Instant now) {
        boolean holiday = holidays.stream().anyMatch(h -> !h.startDate().isAfter(date) && !h.endDate().isBefore(date));
        List<ShiftRequest.Interval> work = new ArrayList<>();
        List<UUID> applied = new ArrayList<>(), pending = new ArrayList<>();
        int base = 0, annual = 0, unpaid = 0;
        boolean overlap = false, leaveAttendanceConflict = false;

        BigDecimal leaveDays = BigDecimal.ZERO;
        var relevant = leaves.stream().filter(l -> !l.startDate().isAfter(date) && !l.endDate().isBefore(date))
                .toList();
        for (var leave : relevant)
            if ("PENDING".equals(leave.status()))
                pending.add(leave.id());
        for (var interval : shift.intervals()) {
            int minutes = (int) Duration.between(ShiftTimes.at(date, shift, interval.start()), ShiftTimes.at(date, shift, interval.end())).toMinutes();
            if (holiday)
                continue;
            base += minutes;
            var covering = relevant.stream().filter(l -> "APPROVED".equals(l.status())
                    && ("FULL_DAY".equals(l.period()) || interval.period().name().equals(l.period()))).toList();
            if (covering.size() > 1)
                overlap = true;
            if (covering.isEmpty())
                work.add(interval);
            else {
                var leave = covering.getFirst();
                if ("ANNUAL".equals(leave.leaveType()))
                    annual += minutes;
                else
                    unpaid += minutes;
                if (!applied.contains(leave.id())) {
                    applied.add(leave.id());
                    leaveDays = leaveDays
                            .add("FULL_DAY".equals(leave.period()) ? BigDecimal.ONE : new BigDecimal("0.5"));
                }
                if (in != null && out != null && seconds(date, shift, interval, in, out) > 0)
                    leaveAttendanceConflict = true;
            }
        }
        int remaining = base - annual - unpaid;
        boolean unmatchedLeave = !holiday
                && relevant.stream().anyMatch(l -> "APPROVED".equals(l.status()) && !applied.contains(l.id()));
        if (overlap || leaveAttendanceConflict || unmatchedLeave)
            return new Result(base, annual, unpaid, leaveDays, remaining, null, null, null, null, null, null,
                    "SOURCE_CONFLICT", pending, applied);
        if (holiday || remaining == 0)
            return new Result(base, annual, unpaid, leaveDays, remaining,
                    in == null && out == null ? 0L : null, in == null && out == null ? 0 : null,
                    0L, 0, 0L, 0, in == null && out == null ? (holiday ? "HOLIDAY" : "ON_LEAVE") : "SOURCE_CONFLICT",
                    pending, applied);
        if (in == null || out == null) {
            Instant cutoff = ShiftTimes.windowEnd(date, shift);
            String status = in == null ? (now.isAfter(cutoff) ? "NO_RECORD" : "NOT_STARTED")
                    : (now.isAfter(cutoff) ? "MISSING_CHECK_OUT" : "OPEN");
            return new Result(base, annual, unpaid, leaveDays, remaining, null, null, null, null, null, null, status,
                    pending, applied);
        }
        if (!out.isAfter(in))
            return new Result(base, annual, unpaid, leaveDays, remaining,
                    null, null, null, null, null, null, "INVALID_RECORD", pending, applied);
        long actual = 0, late = 0, early = 0;
        for (var interval : work) {
            Instant start = ShiftTimes.at(date, shift, interval.start());
            Instant end = ShiftTimes.at(date, shift, interval.end());
            actual += seconds(date, shift, interval, in, out);
            late += Math.max(0, Duration.between(start, in.isBefore(end) ? in : end).getSeconds());
            early += Math.max(0, Duration.between(out.isAfter(start) ? out : start, end).getSeconds());
        }
        int roundedLate = round15(late), roundedEarly = round15(early);
        return new Result(base, annual, unpaid, leaveDays, remaining, actual,
                Math.max(0, remaining - roundedLate - roundedEarly), late, roundedLate, early, roundedEarly,
                "CLOSED", pending, applied);
    }

    public static int round15(long seconds) {
        return Math.toIntExact((Math.max(0, seconds) + 899) / 900 * 15);
    }

    private long seconds(LocalDate date, ShiftRequest shift, ShiftRequest.Interval interval, Instant in, Instant out) {
        Instant start = ShiftTimes.at(date, shift, interval.start()),
                end = ShiftTimes.at(date, shift, interval.end());
        return Math.max(0,
                Duration.between(in.isAfter(start) ? in : start, out.isBefore(end) ? out : end).getSeconds());
    }
}
