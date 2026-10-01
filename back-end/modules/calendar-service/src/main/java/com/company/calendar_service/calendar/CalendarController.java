package com.company.calendar_service.calendar;

import com.company.calendar_service.dto.CalendarResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.ZoneId;

@RestController
@RequestMapping("/api/v1/calendar")
public class CalendarController {
    private final CalendarService service;

    public CalendarController(CalendarService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<CalendarResponse> get(@RequestParam(required = false) Integer year) {
        int selected = year == null ? LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).getYear() : year;
        if (selected < 1900 || selected > 2100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "year must be between 1900 and 2100");
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.read(selected));
    }
}
