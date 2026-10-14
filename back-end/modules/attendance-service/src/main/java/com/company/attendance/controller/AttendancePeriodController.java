package com.company.attendance.controller;
import com.company.attendance.service.AttendancePeriodService;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.util.List;
@RestController @RequestMapping("/api/v1/attendance/periods")
@PreAuthorize("hasAnyRole('HR','ADMIN')") @RequiredArgsConstructor
public class AttendancePeriodController {
    private final AttendancePeriodService service;
    public record Change(@NotBlank @Size(max=2000) String reason) {}
    @GetMapping("/{month}")
    public AttendancePeriodService.Status get(@PathVariable String month, JwtAuthenticationToken actor) { return service.get(month, actor); }
    @GetMapping("/{month}/history")
    public List<AttendancePeriodService.Revision> history(@PathVariable String month, JwtAuthenticationToken actor) { return service.history(month, actor); }
    @GetMapping("/{month}/check-sources")
    public AttendancePeriodService.SourceCheck checkSources(@PathVariable String month, JwtAuthenticationToken actor) {
        return service.checkSources(month, actor);
    }
    @PostMapping("/{month}/close")
    public AttendancePeriodService.Status close(@PathVariable String month, @RequestHeader(value="If-Match", required=false) String match,
            @Valid @RequestBody Change body, JwtAuthenticationToken actor) { return service.change(month, true, match, body.reason(), actor); }
    @PostMapping("/{month}/reopen")
    public AttendancePeriodService.Status reopen(@PathVariable String month, @RequestHeader(value="If-Match", required=false) String match,
            @Valid @RequestBody Change body, JwtAuthenticationToken actor) { return service.change(month, false, match, body.reason(), actor); }
}
