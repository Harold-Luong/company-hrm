package com.company.attendance.controller;

import com.company.attendance.dto.*;
import com.company.attendance.dto.ScheduleResponse.*;
import com.company.attendance.service.ScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.*;
import static com.company.attendance.service.ApiRules.employee;

@RestController
@RequestMapping("/api/v1/attendance/schedules")
@RequiredArgsConstructor
public class ScheduleController {
    private final ScheduleService service;

    @GetMapping("/history")
    @PreAuthorize("hasAnyRole('HR','ADMIN')")
    public PageResponse<com.company.attendance.entity.ScheduleBatch> history(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.history(page, size);
    }

    @PostMapping("/preview")
    @PreAuthorize("hasAnyRole('HR','ADMIN')")
    public ResponseEntity<Preview> preview(@Valid @RequestBody ScheduleRequest body, JwtAuthenticationToken actor) {
        var result = service.preview(body, actor);
        return ResponseEntity.ok().eTag("\"" + result.scheduleRevision() + "\"").body(result);
    }

    @PostMapping("/apply")
    @PreAuthorize("hasAnyRole('HR','ADMIN')")
    public Applied apply(@Valid @RequestBody ScheduleRequest body,
            @RequestHeader(value = "If-Match", required = false) String match,
            @RequestHeader(value = "Idempotency-Key", required = false) String key, JwtAuthenticationToken actor) {
        return service.apply(body, match, key, actor);
    }

    @GetMapping("/mine")
    public List<Day> mine(@RequestParam LocalDate from, @RequestParam LocalDate until, JwtAuthenticationToken actor) {
        return service.schedule(employee(actor), from, until, actor);
    }

    @GetMapping("/employees/{employeeId}")
    @PreAuthorize("hasAnyRole('HR','ADMIN')")
    public List<Day> employeeSchedule(@PathVariable UUID employeeId, @RequestParam LocalDate from,
            @RequestParam LocalDate until, JwtAuthenticationToken actor) {
        return service.schedule(employeeId, from, until, actor);
    }
}
