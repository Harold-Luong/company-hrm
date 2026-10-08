package com.company.attendance.controller;

import com.company.attendance.dto.AttendanceResponse;
import com.company.attendance.entity.AttendanceEvent;
import com.company.attendance.security.CorporateNetwork;
import com.company.attendance.service.AttendanceService;
import com.company.attendance.service.AttendanceCsvExporter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/v1/attendance")
@RequiredArgsConstructor
public class AttendanceController {
    private final AttendanceService service;
    private final AttendanceCsvExporter csvExporter;
    private final CorporateNetwork network;

    @PostMapping("/check-in")
    public AttendanceResponse checkIn(@RequestHeader(value = "Idempotency-Key", required = false) String key,
            HttpServletRequest request, JwtAuthenticationToken actor) {
        return service.punch(true, key, network.requireAllowed(request), actor);
    }

    @PostMapping("/check-out")
    public AttendanceResponse checkOut(@RequestHeader(value = "Idempotency-Key", required = false) String key,
            HttpServletRequest request, JwtAuthenticationToken actor) {
        return service.punch(false, key, network.requireAllowed(request), actor);
    }

    @GetMapping("/mine")
    public List<AttendanceResponse> mine(@RequestParam LocalDate from, @RequestParam LocalDate until,
            JwtAuthenticationToken actor) {
        return service.mine(from, until, actor);
    }

    @GetMapping("/reports")
    @PreAuthorize("hasAnyRole('HR','ADMIN')")
    public List<AttendanceResponse> reports(@RequestParam LocalDate from, @RequestParam LocalDate until,
            @RequestParam(required = false) UUID employeeId, @RequestParam(required = false) UUID departmentId,
            JwtAuthenticationToken actor) {
        return service.report(employeeId, departmentId, from, until, actor);
    }

    @GetMapping("/reports/export.csv")
    @PreAuthorize("hasAnyRole('HR','ADMIN')")
    public ResponseEntity<byte[]> export(@RequestParam LocalDate from, @RequestParam LocalDate until,
            @RequestParam(required = false) UUID employeeId, @RequestParam(required = false) UUID departmentId,
            JwtAuthenticationToken actor) {
        var rows = service.report(employeeId, departmentId, from, until, actor);
        return ResponseEntity.ok().contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"attendance-" + from + "-" + until + ".csv\"")
                .body(csvExporter.export(rows));
    }

    @PostMapping("/employees/{employeeId}/days/{date}/refresh-coverage")
    @PreAuthorize("hasAnyRole('HR','ADMIN')")
    public AttendanceResponse refresh(@PathVariable UUID employeeId, @PathVariable LocalDate date,
            @RequestHeader(value = "If-Match", required = false) String match, JwtAuthenticationToken actor) {
        return service.refresh(employeeId, date, match, actor);
    }

    @GetMapping("/employees/{employeeId}/days/{date}/events")
    public List<AttendanceEvent> history(@PathVariable UUID employeeId, @PathVariable LocalDate date,
            JwtAuthenticationToken actor) {
        return service.history(employeeId, date, actor);
    }
}
