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
        @NotEmpty @Size(max = 2) List<@NotNull @Valid Interval> intervals,
        @NotNull LocalTime checkInFrom,
        @NotNull LocalTime checkOutUntil, Boolean overnight) {
    public ShiftRequest { overnight = Boolean.TRUE.equals(overnight); }
    public ShiftRequest(String name, AttendanceMode mode, String timezone, List<Interval> intervals, LocalTime checkInFrom, LocalTime checkOutUntil) {
        this(name, mode, timezone, intervals, checkInFrom, checkOutUntil, false);
    }
    public record Interval(@NotNull WorkPeriod period, @NotNull LocalTime start, @NotNull LocalTime end) {}
}
