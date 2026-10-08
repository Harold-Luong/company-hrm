package com.company.attendance.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ScheduleResponse {
    private ScheduleResponse() {}
    public record Day(UUID employeeId, LocalDate date, UUID shiftId, Long shiftVersion, ShiftRequest definition,
                      int requiredMinutes, String source) {}
    public record Preview(long scheduleRevision, ShiftResponse shift, int affectedRuleCount,
                          List<UUID> affectedOverrides, int recordedDayConflicts, int approvedLeaveConflicts,
                          int approvedRequestConflicts, int approvedOvertimeConflicts) {}
    public record Applied(UUID batchId, long scheduleRevision, int replacedRules, int createdRules) {}
}
