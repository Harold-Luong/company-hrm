package com.company.attendance.service.worktime;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * knownPayableMinutes is explicitly a subtotal; payableMinutes stays null until
 * regular attendance is complete.
 */
public record WorkTimePeriodResult(List<WorkTimeResult> days, List<LocalDate> incompleteDates,
        int knownPayableMinutes, Integer payableMinutes, BigDecimal payableDays, Integer countedOvertimeMinutes) {
    public WorkTimePeriodResult {
        days = List.copyOf(days);
        incompleteDates = List.copyOf(incompleteDates);
    }

    public boolean complete() {
        return incompleteDates.isEmpty();
    }
}
