package com.company.attendance.dto;

import com.company.attendance.enums.ScheduleScope;
import jakarta.validation.constraints.*;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public record ScheduleRequest(@NotNull UUID shiftId, @PositiveOrZero long shiftVersion,
        @NotNull ScheduleScope scope, @NotNull @Size(max = 100) Set<@NotNull UUID> employeeIds,
        @NotNull LocalDate from, LocalDate until,
        @NotEmpty Set<@NotNull DayOfWeek> weekdays,
        @NotBlank @Size(max = 1000) String reason) {}
