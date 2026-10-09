package com.company.attendance.dto;

import com.company.attendance.enums.AttendanceRequestStatus;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.UUID;

public final class AttendanceCorrectionModels {
    private AttendanceCorrectionModels() {}
    public record Write(@NotNull LocalDate workDate, @NotNull UUID shiftId,
            @NotNull @PositiveOrZero Long shiftVersion, @PositiveOrZero Long recordVersion,
            @NotNull Instant proposedCheckIn, @NotNull Instant proposedCheckOut,
            @NotBlank @Size(max = 1000) String reason) {}
    public record Response(UUID id, long version, UUID employeeId, String employeeCode, String employeeName,
            LocalDate workDate, UUID shiftId, long shiftVersion, ShiftRequest definition,
            Long baseRecordVersion, Instant beforeCheckIn, Instant beforeCheckOut,
            Instant proposedCheckIn, Instant proposedCheckOut, String reason, AttendanceRequestStatus status,
            String reviewNote, Instant createdAt, Instant updatedAt, String reviewedBy, Instant reviewedAt) {}
}
