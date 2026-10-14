package com.company.attendance.controller;

import com.company.attendance.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.time.LocalDate;
import java.util.*;
import static com.company.attendance.service.ApiRules.*;

/** Schedule-only projection: never calls Leave, avoiding a circular REST dependency. */
@RestController
@RequestMapping("/api/v1/attendance/leave-calendar")
@RequiredArgsConstructor
public class LeaveCalendarController {
    private final ScheduleService schedules;
    private final WorkforceClient source;
    public record Day(LocalDate date, UUID shiftId, Long shiftVersion, int fullUnits,
                      int morningUnits, int afternoonUnits) {}
    @GetMapping
    public List<Day> calendar(@RequestParam UUID employeeId, @RequestParam LocalDate from,
            @RequestParam LocalDate until, JwtAuthenticationToken actor) {
        if (!employeeId.equals(employee(actor))) requireReviewer(actor);
        ScheduleService.dateRange(from, until);
        var holidays = source.holidays(from, until, actor);
        var person = source.employee(employeeId, actor);
        return schedules.schedule(employeeId, from, until, actor).stream().map(day -> {
            boolean off = day.definition() == null || day.date().isBefore(person.hireDate())
                || holidays.stream().anyMatch(h -> !day.date().isBefore(h.startDate()) && !day.date().isAfter(h.endDate()));
            var periods = off ? Set.<String>of() : day.definition().intervals().stream()
                .map(i -> i.period().name()).collect(java.util.stream.Collectors.toSet());
            return new Day(day.date(), day.shiftId(), day.shiftVersion(), off ? 0 : 2,
                periods.contains("MORNING") ? 1 : 0, periods.contains("AFTERNOON") ? 1 : 0);
        }).toList();
    }
}
