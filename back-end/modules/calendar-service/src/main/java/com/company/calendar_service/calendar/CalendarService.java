package com.company.calendar_service.calendar;

import com.company.calendar_service.dto.CalendarEventResponse;
import com.company.calendar_service.dto.CalendarResponse;
import com.company.calendar_service.entity.CalendarEvent;
import com.company.calendar_service.enums.AudienceType;
import com.company.calendar_service.enums.CalendarEventStatus;
import com.company.calendar_service.repository.CalendarEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Stream;

@Service
public class CalendarService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final List<CalendarEventStatus> VISIBLE_STATUSES = List.of(
            CalendarEventStatus.PUBLISHED, CalendarEventStatus.CANCELLED);
    private final CalendarEventRepository repository;

    public CalendarService(CalendarEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CalendarResponse read(int year) {
        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate until = from.plusYears(1);
        List<CalendarEvent> allDayEvents = repository
                .findByStatusInAndAudienceTypeAndAllDayTrueAndStartDateLessThanAndEndDateGreaterThanEqual(
                        VISIBLE_STATUSES, AudienceType.ALL, until, from);
        List<CalendarEvent> timedEvents = repository
                .findByStatusInAndAudienceTypeAndAllDayFalseAndStartAtLessThanAndEndAtGreaterThan(
                        VISIBLE_STATUSES, AudienceType.ALL,
                        until.atStartOfDay(ZONE).toInstant(), from.atStartOfDay(ZONE).toInstant());
        List<CalendarEventResponse> events = Stream.concat(allDayEvents.stream(), timedEvents.stream())
                .sorted(Comparator.comparing(this::startInstant).thenComparing(CalendarEvent::getId))
                .map(CalendarEventResponse::from)
                .toList();

        TreeSet<Integer> years = new TreeSet<>();
        for (var period : repository.findByStatusInAndAudienceType(VISIBLE_STATUSES, AudienceType.ALL)) {
            int firstYear = period.isAllDay() ? period.getStartDate().getYear()
                    : period.getStartAt().atZone(ZONE).getYear();
            // Timed events exclude their end instant, including midnight on January 1.
            int lastYear = period.isAllDay() ? period.getEndDate().getYear()
                    : period.getEndAt().minusNanos(1).atZone(ZONE).getYear();
            for (int value = Math.max(1900, firstYear); value <= Math.min(2100, lastYear); value++) {
                years.add(value);
            }
        }
        years.add(year);
        years.add(LocalDate.now(ZONE).getYear());
        return new CalendarResponse(year, List.copyOf(years), events);
    }

    private Instant startInstant(CalendarEvent event) {
        return event.isAllDay() ? event.getStartDate().atStartOfDay(ZONE).toInstant() : event.getStartAt();
    }
}
