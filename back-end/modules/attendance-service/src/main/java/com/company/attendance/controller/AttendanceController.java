package com.company.attendance.controller;

import com.company.attendance.dto.AttendanceResponse;
import com.company.attendance.entity.AttendanceEvent;
import com.company.attendance.security.CorporateNetwork;
import com.company.attendance.service.AttendanceService;
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
        StringBuilder csv = new StringBuilder(
                "\uFEFFemployee_id,employee_code,employee_name,department_id,work_date,shift_id,shift_version,check_in,check_out,required_minutes,annual_leave_minutes,unpaid_leave_minutes,leave_days,remaining_minutes,actual_seconds,counted_minutes,late_seconds,late_minutes,early_seconds,early_minutes,status,pending_leave_ids,applied_leave_ids,source_observed_at,record_version,report_state\r\n");
        for (var r : rows) {
            Object[] values = { r.employeeId(), r.employeeCode(), r.employeeName(), r.departmentId(), r.workDate(),
                    r.shiftId(), r.shiftVersion(),
                    r.checkIn(), r.checkOut(), r.baseRequiredMinutes(), r.annualLeaveMinutes(), r.unpaidLeaveMinutes(),
                    r.leaveDays(),
                    r.remainingRequiredMinutes(), r.workedActualSeconds(), r.workMinutesCounted(),
                    r.lateActualSeconds(), r.roundedLateMinutes(),
                    r.earlyActualSeconds(), r.roundedEarlyMinutes(), r.status(), r.pendingLeaveIds(),
                    r.appliedLeaveIds(),
                    r.sourceObservedAt(), r.recordVersion(), r.reportState() };
            csv.append(String.join(",", Arrays.stream(values).map(AttendanceController::csvCell).toList()))
                    .append("\r\n");
        }
        return ResponseEntity.ok().contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"attendance-" + from + "-" + until + ".csv\"")
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
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

    static String csvCell(Object value) {
        String text = value == null ? "" : value.toString();
        String stripped = text.stripLeading();
        if (!stripped.isEmpty() && "=+-@".indexOf(stripped.charAt(0)) >= 0
                || text.startsWith("\t") || text.startsWith("\r") || text.startsWith("\n"))
            text = "'" + text;
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
}
