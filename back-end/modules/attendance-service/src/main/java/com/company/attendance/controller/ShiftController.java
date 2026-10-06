package com.company.attendance.controller;

import com.company.attendance.dto.*;
import com.company.attendance.entity.ShiftRevision;
import com.company.attendance.service.ShiftService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/attendance/shifts")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('HR','ADMIN')")
public class ShiftController {
    private final ShiftService service;
    @GetMapping
    public PageResponse<ShiftResponse> list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) { return service.list(page, size); }
    @GetMapping("/{id}")
    public ResponseEntity<ShiftResponse> get(@PathVariable UUID id) { return response(service.get(id)); }
    @GetMapping("/{id}/history")
    public PageResponse<ShiftRevision> history(@PathVariable UUID id, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) { return service.history(id, page, size); }
    @PostMapping
    public ResponseEntity<ShiftResponse> create(@Valid @RequestBody ShiftRequest body, JwtAuthenticationToken actor) {
        var result = service.create(body, actor);
        return ResponseEntity.created(URI.create("/api/v1/attendance/shifts/" + result.id()))
                .eTag("\"" + result.version() + "\"").body(result);
    }
    @PutMapping("/{id}")
    public ResponseEntity<ShiftResponse> update(@PathVariable UUID id, @Valid @RequestBody ShiftRequest body,
            @RequestHeader(value = "If-Match", required = false) String match, JwtAuthenticationToken actor) {
        return response(service.update(id, body, match, actor));
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<ShiftResponse> deactivate(@PathVariable UUID id,
            @RequestHeader(value = "If-Match", required = false) String match, JwtAuthenticationToken actor) {
        return response(service.deactivate(id, match, actor));
    }
    private ResponseEntity<ShiftResponse> response(ShiftResponse result) {
        return ResponseEntity.ok().eTag("\"" + result.version() + "\"").body(result);
    }
}
