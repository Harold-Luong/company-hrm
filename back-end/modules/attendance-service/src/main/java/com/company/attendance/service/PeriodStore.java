package com.company.attendance.service;

import com.company.attendance.exception.ExceptionMessages;
import com.company.attendance.repository.*;
import com.company.attendance.dto.AttendanceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import static com.company.attendance.service.ApiRules.*;

/** All writes and close/reopen serialize on ScheduleState, including initially absent months. */
@Service @RequiredArgsConstructor
public class PeriodStore {
    private final AttendancePeriodRepository periods;
    private final ObjectMapper mapper;
    public void requireOpen(LocalDate date) {
        if (periods.findById(YearMonth.from(date).toString()).map(p -> p.isClosed()).orElse(false))
            throw error(HttpStatus.CONFLICT, ExceptionMessages.PERIOD_CLOSED);
    }
    public void requireOpen(LocalDate from, LocalDate until) {
        if (periods.findAll().stream().anyMatch(p -> p.isClosed()
                && !YearMonth.parse(p.getMonth()).atDay(1).isAfter(until)
                && !YearMonth.parse(p.getMonth()).atEndOfMonth().isBefore(from)))
            throw error(HttpStatus.CONFLICT, ExceptionMessages.SCHEDULE_OVERLAPS_CLOSED_PERIOD);
    }
    public Optional<List<AttendanceResponse>> frozen(YearMonth month) {
        return periods.findById(month.toString()).filter(p -> p.isClosed())
            .map(p -> List.of(mapper.readValue(p.getReportJson(), AttendanceResponse[].class)));
    }
}
