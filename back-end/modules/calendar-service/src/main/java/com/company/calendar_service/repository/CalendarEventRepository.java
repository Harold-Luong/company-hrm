package com.company.calendar_service.repository;

import com.company.calendar_service.entity.CalendarEvent;
import com.company.calendar_service.enums.AudienceType;
import com.company.calendar_service.enums.CalendarEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, Long> {
    @Query(value = """
            SELECT * FROM calendar_events
            WHERE ((all_day AND start_date <= :toDate AND end_date >= :fromDate)
                OR (NOT all_day AND start_at < :untilTime AND end_at > :fromTime))
              AND (CAST(:type AS text) IS NULL OR type = :type)
              AND (CAST(:status AS text) IS NULL OR status = :status)
            ORDER BY CASE WHEN status = 'DRAFT' THEN 0 ELSE 1 END,
                ABS(COALESCE(start_date, CAST(start_at AT TIME ZONE timezone AS date)) - CAST(:today AS date)),
                COALESCE(start_at, CAST(start_date AS timestamp) AT TIME ZONE timezone), id
            """, countQuery = """
            SELECT count(*) FROM calendar_events
            WHERE ((all_day AND start_date <= :toDate AND end_date >= :fromDate)
                OR (NOT all_day AND start_at < :untilTime AND end_at > :fromTime))
              AND (CAST(:type AS text) IS NULL OR type = :type)
              AND (CAST(:status AS text) IS NULL OR status = :status)
            """, nativeQuery = true)
    Page<CalendarEvent> search(LocalDate fromDate, LocalDate toDate, Instant fromTime,
            Instant untilTime, String type, String status, LocalDate today, Pageable pageable);

    List<CalendarEvent> findByStatusInAndAudienceTypeAndAllDayTrueAndStartDateLessThanAndEndDateGreaterThanEqual(
            Collection<CalendarEventStatus> statuses, AudienceType audienceType,
            LocalDate untilDate, LocalDate fromDate);

    List<CalendarEvent> findByStatusInAndAudienceTypeAndAllDayFalseAndStartAtLessThanAndEndAtGreaterThan(
            Collection<CalendarEventStatus> statuses, AudienceType audienceType,
            Instant untilTime, Instant fromTime);

    List<EventPeriod> findByStatusInAndAudienceType(
            Collection<CalendarEventStatus> statuses, AudienceType audienceType);

    // Only read dates/times when collecting the available years.
    interface EventPeriod {
        boolean isAllDay();
        LocalDate getStartDate();
        LocalDate getEndDate();
        Instant getStartAt();
        Instant getEndAt();
    }
}
