package com.company.calendar_service.dto;

import com.company.calendar_service.entity.CalendarEvent;
import com.company.calendar_service.enums.AudienceType;
import com.company.calendar_service.enums.CalendarEventStatus;
import com.company.calendar_service.enums.CalendarEventType;
import com.company.calendar_service.enums.HolidayKind;

import java.time.Instant;
import java.time.LocalDate;

public record CalendarEventResponse(
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
        AudienceType audienceType) {

    public static CalendarEventResponse from(CalendarEvent event) {
        return new CalendarEventResponse(
                event.getId(), event.getTitle(), event.getDescription(), event.getType(), event.getHolidayKind(),
                event.isAllDay(), event.getStartDate(), event.getEndDate(), event.getStartAt(), event.getEndAt(),
                event.getTimezone(), event.getLocation(), event.getStatus(), event.getAudienceType());
    }
}
