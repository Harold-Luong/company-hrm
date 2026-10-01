package com.company.calendar_service.dto;

import com.company.calendar_service.enums.*;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.time.LocalDate;

public record CalendarEventRequest(
        @NotBlank @Size(max = 255) String title,
        @Size(max = 10000) String description,
        @NotNull CalendarEventType type,
        HolidayKind holidayKind,
        @NotNull Boolean allDay,
        LocalDate startDate, LocalDate endDate,
        Instant startAt, Instant endAt,
        @NotBlank @Pattern(regexp = "Asia/Ho_Chi_Minh") String timezone,
        @Size(max = 255) String location,
        @NotNull AudienceType audienceType,
        @Size(max = 1000) String reason) {
    public CalendarEventRequest {
        title = trim(title);
        description = trim(description);
        timezone = trim(timezone);
        location = trim(location);
        reason = trim(reason);
    }

    @JsonAnySetter
    public void rejectUnknown(String name, Object value) {
        throw new IllegalArgumentException("Unsupported request field: " + name);
    }

    private static String trim(String value) {
        return value == null ? null : value.strip();
    }
}
