package com.company.calendar_service.calendar;

import com.company.calendar_service.dto.*;
import com.company.calendar_service.entity.CalendarEvent;
import com.company.calendar_service.enums.*;
import com.company.calendar_service.repository.CalendarEventRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.time.temporal.ChronoUnit;

@Service
@Transactional
public class CalendarManagementService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final CalendarEventRepository repository;
    private final JdbcTemplate jdbc;

    public CalendarManagementService(CalendarEventRepository repository, JdbcTemplate jdbc) {
        this.repository = repository;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public CalendarEventPage list(LocalDate from, LocalDate to, CalendarEventType type,
            CalendarEventStatus status, int page, int size) {
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) >= 366
                || page < 0 || size < 1 || size > 100) {
            throw problem(HttpStatus.BAD_REQUEST, "Invalid date range or pagination (maximum 366 days, size 1-100)");
        }
        var result = repository.search(from, to, from.atStartOfDay(ZONE).toInstant(),
                to.plusDays(1).atStartOfDay(ZONE).toInstant(),
                type == null ? null : type.name(), status == null ? null : status.name(),
                LocalDate.now(ZONE), PageRequest.of(page, size));
        return new CalendarEventPage(result.getContent().stream().map(ManagedCalendarEventResponse::from).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public ManagedCalendarEventResponse get(long id) {
        return ManagedCalendarEventResponse.from(find(id));
    }

    public ManagedCalendarEventResponse create(CalendarEventRequest request, String actor) {
        validateDates(request);
        var event = new CalendarEvent();
        apply(event, request);
        event.setStatus(CalendarEventStatus.DRAFT);
        event.setCreatedBy(actor);
        event.setCreatedAt(Instant.now());
        stamp(event, actor);
        repository.saveAndFlush(event);
        audit(event.getId(), "CREATE", actor, request.reason(), null, snapshot(event.getId()));
        return ManagedCalendarEventResponse.from(event);
    }

    public ManagedCalendarEventResponse update(long id, String match, CalendarEventRequest request, String actor) {
        var event = matching(id, match);
        validateDates(request);
        if (event.getStatus() == CalendarEventStatus.CANCELLED) {
            throw problem(HttpStatus.CONFLICT, "Cancelled events cannot be changed");
        }
        if (event.getStatus() == CalendarEventStatus.PUBLISHED) {
            requireFuture(event);
            requireReason(request.reason());
            if (event.getType() != request.type() || event.getHolidayKind() != request.holidayKind()
                    || event.isAllDay() != request.allDay() || event.getAudienceType() != request.audienceType()) {
                throw problem(HttpStatus.CONFLICT, "Published event classification cannot be changed");
            }
        }
        String before = snapshot(id);
        apply(event, request);
        if (event.getStatus() == CalendarEventStatus.PUBLISHED) requireFuture(event);
        stamp(event, actor);
        repository.flush();
        audit(id, "UPDATE", actor, request.reason(), before, snapshot(id));
        return ManagedCalendarEventResponse.from(event);
    }

    public ManagedCalendarEventResponse publish(long id, String match, String actor) {
        var event = matching(id, match);
        if (event.getStatus() != CalendarEventStatus.DRAFT) {
            throw problem(HttpStatus.CONFLICT, "Only draft events can be published");
        }
        if (event.isAllDay()) {
            if (event.getStartDate().isBefore(LocalDate.now(ZONE))) {
                throw problem(HttpStatus.CONFLICT, "Cannot publish an event in the past");
            }
        } else requireFuture(event);
        String before = snapshot(id);
        event.setStatus(CalendarEventStatus.PUBLISHED);
        stamp(event, actor);
        repository.flush();
        audit(id, "PUBLISH", actor, null, before, snapshot(id));
        return ManagedCalendarEventResponse.from(event);
    }

    public ManagedCalendarEventResponse cancel(long id, String match, String reason, String actor) {
        var event = matching(id, match);
        if (event.getStatus() != CalendarEventStatus.PUBLISHED) {
            throw problem(HttpStatus.CONFLICT, "Only published events can be cancelled");
        }
        requireFuture(event);
        requireReason(reason);
        String before = snapshot(id);
        event.setStatus(CalendarEventStatus.CANCELLED);
        stamp(event, actor);
        repository.flush();
        audit(id, "CANCEL", actor, reason, before, snapshot(id));
        return ManagedCalendarEventResponse.from(event);
    }

    public void delete(long id, String match, String actor) {
        var event = matching(id, match);
        if (event.getStatus() != CalendarEventStatus.DRAFT) {
            throw problem(HttpStatus.CONFLICT, "Only draft events can be deleted; cancel published events instead");
        }
        String before = snapshot(id);
        repository.delete(event);
        repository.flush();
        audit(id, "DELETE", actor, null, before, null);
    }

    private CalendarEvent find(long id) {
        return repository.findById(id)
                .orElseThrow(() -> problem(HttpStatus.NOT_FOUND, "Calendar event not found"));
    }

    private CalendarEvent matching(long id, String match) {
        if (match == null) throw problem(HttpStatus.PRECONDITION_REQUIRED, "If-Match is required");
        long version;
        try {
            if (!match.matches("\"[0-9]+\"")) throw new NumberFormatException();
            version = Long.parseLong(match.substring(1, match.length() - 1));
        } catch (NumberFormatException error) {
            throw problem(HttpStatus.BAD_REQUEST, "If-Match must contain one quoted non-negative version");
        }
        var event = find(id);
        if (event.getVersion() != version) {
            throw problem(HttpStatus.PRECONDITION_FAILED, "Event has changed; reload before retrying");
        }
        return event;
    }

    private void validateDates(CalendarEventRequest request) {
        boolean valid;
        if (request.allDay()) {
            valid = request.startDate() != null && request.endDate() != null
                    && !request.endDate().isBefore(request.startDate())
                    && request.startAt() == null && request.endAt() == null;
        } else {
            valid = request.startAt() != null && request.endAt() != null
                    && request.endAt().isAfter(request.startAt())
                    && request.startDate() == null && request.endDate() == null;
        }
        if (!valid) throw problem(HttpStatus.BAD_REQUEST, "Invalid all-day or timed event range");
        if (request.type() == CalendarEventType.HOLIDAY) {
            if (!request.allDay() || request.holidayKind() == null) {
                throw problem(HttpStatus.BAD_REQUEST, "Holiday events require allDay and holidayKind");
            }
        } else if (request.holidayKind() != null) {
            throw problem(HttpStatus.BAD_REQUEST, "holidayKind is only allowed for holidays");
        }
    }

    private void requireFuture(CalendarEvent event) {
        Instant start = event.isAllDay() ? event.getStartDate().atStartOfDay(ZONE).toInstant() : event.getStartAt();
        if (!start.isAfter(Instant.now())) {
            throw problem(HttpStatus.CONFLICT, "Events that have started cannot be changed or cancelled");
        }
    }

    private void requireReason(String reason) {
        if (reason == null || reason.isBlank()) throw problem(HttpStatus.BAD_REQUEST, "A reason is required");
    }

    private void apply(CalendarEvent event, CalendarEventRequest request) {
        event.setTitle(request.title());
        event.setDescription(request.description());
        event.setType(request.type());
        event.setHolidayKind(request.holidayKind());
        event.setAllDay(request.allDay());
        event.setStartDate(request.startDate());
        event.setEndDate(request.endDate());
        event.setStartAt(request.startAt());
        event.setEndAt(request.endAt());
        event.setTimezone(request.timezone());
        event.setLocation(request.location());
        event.setAudienceType(request.audienceType());
    }

    private void stamp(CalendarEvent event, String actor) {
        event.setUpdatedBy(actor);
        event.setUpdatedAt(Instant.now());
    }

    private String snapshot(long id) {
        return jdbc.queryForObject("SELECT to_jsonb(e)::text FROM calendar_events e WHERE id = ?", String.class, id);
    }

    private void audit(long id, String action, String actor, String reason, String before, String after) {
        jdbc.update("""
                INSERT INTO calendar_event_audit
                    (calendar_event_id, action, actor_user_id, reason, before_data, after_data)
                VALUES (?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb))
                """, id, action, actor, reason, before, after);
    }

    private ResponseStatusException problem(HttpStatus status, String detail) {
        return new ResponseStatusException(status, detail);
    }
}
