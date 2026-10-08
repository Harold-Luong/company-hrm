package com.company.attendance.controller;

import com.company.attendance.dto.*;
import com.company.attendance.dto.AttendanceRequestModels.*;
import com.company.attendance.entity.AttendanceRequestHistory;
import com.company.attendance.enums.AttendanceRequestStatus;
import com.company.attendance.service.AttendanceRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/attendance/requests")
@RequiredArgsConstructor
public class AttendanceRequestController {
    private final AttendanceRequestService service;

    @GetMapping
    public PageResponse<Response> mine(@RequestParam(required = false) AttendanceRequestStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            JwtAuthenticationToken actor) {
        return service.list(false, status, page, size, actor);
    }

    @GetMapping("/inbox")
    @PreAuthorize("hasAnyRole('HR','ADMIN')")
    public PageResponse<Response> inbox(@RequestParam(required = false) AttendanceRequestStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            JwtAuthenticationToken actor) {
        return service.list(true, status, page, size, actor);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Response> get(@PathVariable UUID id, JwtAuthenticationToken actor) {
        return response(service.get(id, actor));
    }

    @GetMapping("/{id}/history")
    public PageResponse<AttendanceRequestHistory> history(@PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            JwtAuthenticationToken actor) {
        return service.history(id, page, size, actor);
    }

    @PostMapping
    public ResponseEntity<Response> create(@Valid @RequestBody Write body,
            @RequestHeader(value = "Idempotency-Key", required = false) String key, JwtAuthenticationToken actor) {
        var result = service.create(body, key, actor);
        return ResponseEntity.created(URI.create("/api/v1/attendance/requests/" + result.id()))
                .eTag("\"" + result.version() + "\"").body(result);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Response> update(@PathVariable UUID id, @Valid @RequestBody Write body,
            @RequestHeader(value = "If-Match", required = false) String match, JwtAuthenticationToken actor) {
        return response(service.update(id, body, match, actor));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Response> cancel(@PathVariable UUID id,
            @RequestHeader(value = "If-Match", required = false) String match, JwtAuthenticationToken actor) {
        return response(service.cancel(id, match, actor));
    }

    @PostMapping("/{id}/decision")
    @PreAuthorize("hasAnyRole('HR','ADMIN')")
    public ResponseEntity<Response> decide(@PathVariable UUID id, @Valid @RequestBody Decision body,
            @RequestHeader(value = "If-Match", required = false) String match, JwtAuthenticationToken actor) {
        return response(service.decide(id, body, match, actor));
    }

    private ResponseEntity<Response> response(Response result) {
        return ResponseEntity.ok().eTag("\"" + result.version() + "\"").body(result);
    }
}
