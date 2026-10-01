package com.company.calendar_service.dto;

import java.util.List;

public record CalendarResponse(int year, List<Integer> availableYears, List<CalendarEventResponse> events) {
}
