package com.company.attendance.dto;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public record AttendanceResponse(UUID employeeId, String employeeCode, String employeeName,
        UUID departmentId, LocalDate workDate, UUID shiftId, Long shiftVersion,
        Instant checkIn, Instant checkOut, int baseRequiredMinutes, int annualLeaveMinutes,
        int unpaidLeaveMinutes, BigDecimal leaveDays, int remainingRequiredMinutes,
        Long workedActualSeconds, Integer workMinutesCounted, Long lateActualSeconds,
        Integer roundedLateMinutes, Long earlyActualSeconds, Integer roundedEarlyMinutes,
        String status, List<UUID> pendingLeaveIds, List<UUID> appliedLeaveIds,
        Instant sourceObservedAt, Long recordVersion, String reportState, AttendancePermissionCoverage permissionCoverage, OvertimeModels.Summary overtime,
        Instant originalCheckIn, Instant originalCheckOut, UUID correctionId) {}
