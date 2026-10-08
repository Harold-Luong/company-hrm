package com.company.attendance.service;

import com.company.attendance.dto.ShiftRequest;
import java.time.*;

/** Work date is always the date on which the shift starts. */
public final class ShiftTimes {
    private ShiftTimes() {}
    public static Instant at(LocalDate date, ShiftRequest shift, LocalTime time) {
        if (shift.overnight() && time.isBefore(shift.intervals().getFirst().start())) date = date.plusDays(1);
        return date.atTime(time).atZone(ZoneId.of(shift.timezone())).toInstant();
    }
    public static Instant windowStart(LocalDate date, ShiftRequest shift) {
        return date.atTime(shift.checkInFrom()).atZone(ZoneId.of(shift.timezone())).toInstant();
    }
    public static Instant windowEnd(LocalDate date, ShiftRequest shift) {
        return (shift.overnight() ? date.plusDays(1) : date).atTime(shift.checkOutUntil()).atZone(ZoneId.of(shift.timezone())).toInstant();
    }
}
