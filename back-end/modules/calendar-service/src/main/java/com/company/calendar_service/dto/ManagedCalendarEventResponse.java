package com.company.calendar_service.dto;

import com.company.calendar_service.entity.CalendarEvent;
import com.company.calendar_service.enums.AudienceType;
import com.company.calendar_service.enums.CalendarEventStatus;
import com.company.calendar_service.enums.CalendarEventType;
import com.company.calendar_service.enums.HolidayKind;

import java.time.Instant;
import java.time.LocalDate;

public record ManagedCalendarEventResponse(
        Long id,
        String title,
        String description,
        CalendarEventType type,
        HolidayKind holidayKind,
        boolean allDay,
        LocalDate startDate,
        LocalDate endDate,
        Instant startAt,
        Instant endAt,
        String timezone,
        String location,
        CalendarEventStatus status,
        AudienceType audienceType,
        long version, String createdBy, String updatedBy, Instant createdAt, Instant updatedAt) {

    public static ManagedCalendarEventResponse from(CalendarEvent event) {
        return new ManagedCalendarEventResponse(
                event.getId(), event.getTitle(), event.getDescription(), event.getType(), event.getHolidayKind(),
                event.isAllDay(), event.getStartDate(), event.getEndDate(), event.getStartAt(), event.getEndAt(),
                event.getTimezone(), event.getLocation(), event.getStatus(), event.getAudienceType(), event.getVersion(), event.getCreatedBy(),
                event.getUpdatedBy(), event.getCreatedAt(), event.getUpdatedAt());
    }
}
