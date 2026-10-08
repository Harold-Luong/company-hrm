package com.company.attendance.service.worktime;

import java.time.*;
import java.util.*;

/**
 * One employee and one work date. All leave/calendar inputs must already belong
 * to this employee/date.
 */
public record WorkTimeInput(LocalDate workDate, ZoneId zone, List<WorkInterval> schedule,
        CalendarDay calendarDay, List<Leave> leaves, Punch attendance, List<Overtime> overtime,
        Instant recordingClosesAt, Instant now, WorkTimePolicy policy) {
    public static final String FULL_DAY = "FULL_DAY";

    public enum CalendarDay {
        WORKDAY, PAID_HOLIDAY
    }

    public enum LeaveType {
        ANNUAL_PAID, OTHER_PAID, UNPAID
    }

    public enum Approval {
        PENDING, APPROVED, REJECTED, CANCELLED
    }

    public WorkTimeInput {
        Objects.requireNonNull(workDate, "workDate");
        Objects.requireNonNull(zone, "zone");
        schedule = List.copyOf(schedule);
        leaves = List.copyOf(leaves);
        overtime = List.copyOf(overtime);
        Objects.requireNonNull(calendarDay, "calendarDay");
        Objects.requireNonNull(attendance, "attendance");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(policy, "policy");
        if (!schedule.isEmpty())
            Objects.requireNonNull(recordingClosesAt, "recordingClosesAt");
    }

    /**
     * Actual timestamps may be missing or reversed; the calculator returns a status
     * for those cases.
     */
    public record Punch(Instant checkIn, Instant checkOut) {
        public static Punch none() {
            return new Punch(null, null);
        }
    }

    /**
     * Use explicit instants for overnight shifts. Intervals must be ordered and
     * have unique period names.
     */
    public record WorkInterval(String period, Instant start, Instant end) {
        public WorkInterval {
            requireText(period, "period");
            if (FULL_DAY.equals(period))
                throw new IllegalArgumentException("FULL_DAY is reserved for leave");
            requireRange(start, end);
        }

        public int minutes() {
            return Math.toIntExact(Duration.between(start, end).toMinutes());
        }
    }

    public record Leave(String id, String period, LeaveType type, Approval status) {
        public Leave {
            requireText(id, "leave id");
            requireText(period, "leave period");
            Objects.requireNonNull(type, "leave type");
            Objects.requireNonNull(status, "leave status");
        }
    }

    /**
     * Separate OT punches; regular attendance never automatically becomes overtime.
     */
    public record Overtime(String id, Approval status, Instant start, Instant end, Punch attendance) {
        public Overtime {
            requireText(id, "overtime id");
            Objects.requireNonNull(status, "overtime status");
            Objects.requireNonNull(attendance, "overtime attendance");
            requireRange(start, end);
        }

        public int minutes() {
            return Math.toIntExact(Duration.between(start, end).toMinutes());
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException(name + " must not be blank");
    }

    private static void requireRange(Instant start, Instant end) {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (!end.isAfter(start) || Duration.between(start, end).compareTo(Duration.ofHours(24)) > 0)
            throw new IllegalArgumentException("Intervals must be positive and at most 24 hours");
        if (start.getNano() != 0 || end.getNano() != 0 || Math.floorMod(start.getEpochSecond(), 60) != 0
                || Math.floorMod(end.getEpochSecond(), 60) != 0)
            throw new IllegalArgumentException("Scheduled intervals must have minute precision");
    }
}
