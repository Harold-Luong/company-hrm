package com.company.attendance.dto;

import com.company.attendance.enums.AttendanceMode;
import com.company.attendance.enums.WorkPeriod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalTime;
import java.util.List;

public record ShiftRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull AttendanceMode mode,
        @NotBlank String timezone,
        @NotEmpty @Size(max = 2) List<@Valid Interval> intervals,
        @NotNull LocalTime checkInFrom,
        @NotNull LocalTime checkOutUntil) {
    public record Interval(@NotNull WorkPeriod period, @NotNull LocalTime start, @NotNull LocalTime end) {}
}
