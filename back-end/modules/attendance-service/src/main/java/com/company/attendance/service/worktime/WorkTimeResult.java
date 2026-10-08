package com.company.attendance.service.worktime;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record WorkTimeResult(LocalDate workDate, Status status, int scheduledMinutes,
        int requiredWorkMinutes, int paidHolidayMinutes, int annualLeaveMinutes, int otherPaidLeaveMinutes,
        int unpaidLeaveMinutes, Long workedActualSeconds, Integer workedCountedMinutes,
        Long lateActualSeconds, Integer roundedLateMinutes, Long earlyActualSeconds, Integer roundedEarlyMinutes,
        Integer payableMinutes, BigDecimal workedDays, BigDecimal paidAbsenceDays, BigDecimal payableDays,
        List<String> pendingLeaveIds, List<String> appliedLeaveIds, List<String> issues,
        int approvedOvertimeMinutes, Integer countedOvertimeMinutes, List<OvertimeResult> overtime) {
    public enum Status {
        CLOSED, HOLIDAY, ON_LEAVE, NO_SCHEDULE, NOT_STARTED, OPEN,
        NO_RECORD, MISSING_CHECK_OUT, INVALID_RECORD, SOURCE_CONFLICT
    }

    public enum OvertimeStatus {
        NOT_APPROVED, NOT_STARTED, OPEN, NO_RECORD, MISSING_CHECK_OUT, CLOSED, INVALID_RECORD, SOURCE_CONFLICT
    }

    public record OvertimeResult(String id, OvertimeStatus status, int approvedMinutes,
            Long actualSeconds, Integer countedMinutes) {
    }

    public WorkTimeResult {
        pendingLeaveIds = List.copyOf(pendingLeaveIds);
        appliedLeaveIds = List.copyOf(appliedLeaveIds);
        issues = List.copyOf(issues);
        overtime = List.copyOf(overtime);
    }

    public boolean complete() {
        return payableMinutes != null && countedOvertimeMinutes != null;
    }
}
