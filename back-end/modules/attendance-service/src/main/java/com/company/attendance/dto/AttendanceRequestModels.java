package com.company.attendance.dto;

import com.company.attendance.enums.*;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.UUID;

public final class AttendanceRequestModels {
    private AttendanceRequestModels() {
    }

    public record Write(@NotNull LocalDate workDate, @NotNull UUID shiftId, @NotNull @PositiveOrZero Long shiftVersion,
            @NotNull AttendanceRequestType requestType, @NotNull WorkPeriod period, @NotNull LocalTime expectedTime,
            @NotBlank @Size(max = 1000) String reason) {
    }

    public record Decision(@NotNull AttendanceRequestStatus status, @Size(max = 1000) String reviewNote) {
    }

    public record Response(UUID id, long version, UUID employeeId, String employeeCode, String employeeName,
            LocalDate workDate, UUID shiftId, long shiftVersion, ShiftRequest definition,
            AttendanceRequestType requestType,
            WorkPeriod period, LocalTime expectedTime, int requestedMinutes, int roundedRequestedMinutes,
            String reason, AttendanceRequestStatus status, String reviewNote, Instant createdAt, Instant updatedAt,
            String reviewedBy, Instant reviewedAt) {
    }
}
