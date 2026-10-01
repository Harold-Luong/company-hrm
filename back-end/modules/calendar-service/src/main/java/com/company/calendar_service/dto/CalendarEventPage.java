package com.company.calendar_service.dto;

import java.util.List;

public record CalendarEventPage(List<ManagedCalendarEventResponse> content,
        int page, int size, long totalElements, int totalPages) {}
