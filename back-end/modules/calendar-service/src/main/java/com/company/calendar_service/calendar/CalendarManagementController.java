package com.company.calendar_service.calendar;

import com.company.calendar_service.dto.*;
import com.company.calendar_service.enums.CalendarEventStatus;
import com.company.calendar_service.enums.CalendarEventType;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/calendar-events")
public class CalendarManagementController {
    private final CalendarManagementService service;

    public CalendarManagementController(CalendarManagementService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<CalendarEventPage> list(@RequestParam LocalDate from, @RequestParam LocalDate to,
            @RequestParam(required = false) CalendarEventType type,
            @RequestParam(required = false) CalendarEventStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.list(from, to, type, status, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ManagedCalendarEventResponse> get(@PathVariable long id) {
        return response(service.get(id));
    }

    @PostMapping
    public ResponseEntity<ManagedCalendarEventResponse> create(@Valid @RequestBody CalendarEventRequest request,
            Authentication authentication) {
        var event = service.create(request, authentication.getName());
        return ResponseEntity.created(URI.create("/api/v1/calendar-events/" + event.id()))
                .cacheControl(CacheControl.noStore()).eTag(tag(event)).body(event);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ManagedCalendarEventResponse> update(@PathVariable long id,
            @RequestHeader(value = "If-Match", required = false) String match,
            @Valid @RequestBody CalendarEventRequest request, Authentication authentication) {
        return response(service.update(id, match, request, authentication.getName()));
    }

    @PatchMapping("/{id}/publish")
    public ResponseEntity<ManagedCalendarEventResponse> publish(@PathVariable long id,
            @RequestHeader(value = "If-Match", required = false) String match, Authentication authentication) {
        return response(service.publish(id, match, authentication.getName()));
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<ManagedCalendarEventResponse> cancel(@PathVariable long id,
            @RequestHeader(value = "If-Match", required = false) String match,
            @Valid @RequestBody CancelCalendarEventRequest request, Authentication authentication) {
        return response(service.cancel(id, match, request.reason(), authentication.getName()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id,
            @RequestHeader(value = "If-Match", required = false) String match, Authentication authentication) {
        service.delete(id, match, authentication.getName());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private ResponseEntity<ManagedCalendarEventResponse> response(ManagedCalendarEventResponse event) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).eTag(tag(event)).body(event);
    }

    private String tag(ManagedCalendarEventResponse event) {
        return "\"" + event.version() + "\"";
    }
}
