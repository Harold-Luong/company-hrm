package com.company.attendance.service.worktime;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Attendance rules supplied by the caller, not statutory or payroll rules. */
public record WorkTimePolicy(int standardDayMinutes, int deviationRoundingMinutes, DayBasis dayBasis) {
    public enum DayBasis {
        STANDARD_DAY, ASSIGNED_SHIFT
    }

    public WorkTimePolicy {
        if (standardDayMinutes < 1 || standardDayMinutes > 1440)
            throw new IllegalArgumentException("standardDayMinutes must be 1..1440");
        if (deviationRoundingMinutes < 1 || deviationRoundingMinutes > 1440)
            throw new IllegalArgumentException("deviationRoundingMinutes must be 1..1440");
        Objects.requireNonNull(dayBasis, "dayBasis");
    }

    public static WorkTimePolicy standard() {
        return new WorkTimePolicy(480, 15, DayBasis.STANDARD_DAY);
    }

    public int roundDeviation(long seconds) {
        long step = deviationRoundingMinutes * 60L;
        return Math.toIntExact((Math.max(0, seconds) + step - 1) / step * deviationRoundingMinutes);
    }

    public BigDecimal toDays(int minutes, int scheduledMinutes) {
        int denominator = dayBasis == DayBasis.ASSIGNED_SHIFT ? scheduledMinutes : standardDayMinutes;
        if (denominator == 0)
            return BigDecimal.ZERO.setScale(6);
        return BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(denominator), 6, RoundingMode.HALF_UP);
    }
}
