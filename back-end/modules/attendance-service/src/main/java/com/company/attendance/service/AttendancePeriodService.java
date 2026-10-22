package com.company.attendance.service;

import com.company.attendance.exception.ExceptionMessages;
import com.company.attendance.entity.*;
import com.company.attendance.repository.*;
import com.company.attendance.dto.AttendanceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import tools.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.*;
import java.util.*;
import static com.company.attendance.service.ApiRules.*;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class AttendancePeriodService {
    private final AttendancePeriodRepository periods;
    private final AttendancePeriodHistoryRepository history;
    private final ScheduleStateRepository state;
    private final AttendanceService attendance;
    private final WorkforceClient source;
    private final ObjectMapper mapper;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public record Status(String month, long version, boolean closed, String actorUserId, Instant changedAt, String reason) {}
    public record Revision(long version, boolean closed, String actorUserId, Instant changedAt, String reason) {}
    public static YearMonth month(String value) {
        try {
            var month = YearMonth.parse(value);
            if (month.getYear() < 2000 || month.getYear() > 9998) throw new IllegalArgumentException();
            return month;
        } catch (RuntimeException e) { throw error(HttpStatus.BAD_REQUEST, ExceptionMessages.INVALID_PERIOD_MONTH); }
    }
    public Status get(String value, JwtAuthenticationToken actor) {
        requireReviewer(actor); month(value);
        return periods.findById(value).map(this::status).orElse(new Status(value, -1, false, null, null, null));
    }
    public List<Revision> history(String value, JwtAuthenticationToken actor) {
        requireReviewer(actor); month(value);
        return history.findByMonthOrderByPeriodVersionDesc(value).stream().map(p ->
            new Revision(p.getPeriodVersion(), p.isClosed(), p.getActorUserId(), p.getOccurredAt(), p.getReason())).toList();
    }
    public record SourceCheck(boolean changed, Instant checkedAt) {}
    public SourceCheck checkSources(String value, JwtAuthenticationToken actor) {
        requireReviewer(actor); var month = month(value);
        var period = periods.findById(value).filter(AttendancePeriod::isClosed)
            .orElseThrow(() -> error(HttpStatus.CONFLICT, ExceptionMessages.PERIOD_NOT_CLOSED));
        return new SourceCheck(!period.getSourceJson().equals(sourceSnapshot(month, actor)), clock.instant());
    }
    private String sourceSnapshot(YearMonth month, JwtAuthenticationToken actor) {
        var leaves = source.leaves(null, month.atDay(1), month.atEndOfMonth(), actor).stream()
            .sorted(Comparator.comparing(l -> l.id().toString())).toList();
        var holidays = source.holidays(month.atDay(1), month.atEndOfMonth(), actor).stream()
            .sorted(Comparator.comparing(WorkforceClient.Holiday::id)).toList();
        return mapper.writeValueAsString(Map.of("leaves", leaves, "holidays", holidays));
    }
    @Transactional
    public Status change(String value, boolean close, String match, String reason, JwtAuthenticationToken actor) {
        requireReviewer(actor);
        var month = month(value);
        if (reason == null || reason.isBlank() || reason.length() > 2000)
            throw error(HttpStatus.BAD_REQUEST, ExceptionMessages.INVALID_PERIOD_REASON);
        state.lockState();
        var current = periods.findById(value);
        requireVersion(match, current.map(AttendancePeriod::getVersion).orElse(-1L));
        if (current.map(AttendancePeriod::isClosed).orElse(false) == close)
            throw error(HttpStatus.CONFLICT, close ? ExceptionMessages.PERIOD_ALREADY_CLOSED : ExceptionMessages.PERIOD_ALREADY_OPEN);
        var row = current.orElseGet(() -> { var p = new AttendancePeriod(); p.setMonth(value); return p; });
        if (close) {
            if (!month.atEndOfMonth().isBefore(LocalDate.now(clock)))
                throw error(HttpStatus.CONFLICT, ExceptionMessages.PERIOD_MONTH_NOT_COMPLETED);
            LocalDate from = month.atDay(1), until = month.atEndOfMonth();
            // Include employees absent from the current directory and pending workflow rows without a daily record.
            for (String table : List.of("attendance_requests", "attendance_corrections", "attendance_overtime_requests")) {
                long count = jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE status = 'PENDING' AND work_date BETWEEN ? AND ?", Long.class, from, until);
                if (count > 0) throw error(HttpStatus.CONFLICT, ExceptionMessages.PENDING_ATTENDANCE_REQUESTS);
            }
            long incompleteOt = jdbc.queryForObject("SELECT count(*) FROM attendance_overtime_requests WHERE status = 'APPROVED' AND work_date BETWEEN ? AND ? AND (check_in IS NULL OR check_out IS NULL)", Long.class, from, until);
            if (incompleteOt > 0) throw error(HttpStatus.CONFLICT, ExceptionMessages.OT_RECORDINGS_INCOMPLETE);
            if (source.leaves(null, from, until, actor).stream().anyMatch(l -> "PENDING".equals(l.status()) || l.adjustmentPending()))
                throw error(HttpStatus.CONFLICT, ExceptionMessages.PENDING_LEAVE_REQUESTS);
            String observedSources = sourceSnapshot(month, actor);
            var rows = attendance.report(null, null, from, until, actor);
            if (rows.isEmpty()) throw error(HttpStatus.CONFLICT, ExceptionMessages.NO_EMPLOYEES_TO_CLOSE);
            if (rows.stream().anyMatch(r -> !Set.of("CLOSED", "ON_LEAVE", "HOLIDAY", "NO_SCHEDULE").contains(r.status())
                    || (r.remainingRequiredMinutes() > 0 && r.workMinutesCounted() == null)
                    || !r.permissionCoverage().conflictingRequestIds().isEmpty()))
                throw error(HttpStatus.CONFLICT, ExceptionMessages.UNRESOLVED_ATTENDANCE_CONFLICTS);
            if (!observedSources.equals(sourceSnapshot(month, actor)))
                throw error(HttpStatus.CONFLICT, ExceptionMessages.PERIOD_SOURCES_CHANGED);
            row.setSourceJson(observedSources);
            row.setReportJson(mapper.writeValueAsString(rows.stream().map(r -> r.withReportState("FINAL")).toList()));
        }
        row.setClosed(close); row.setActorUserId(actor.getName()); row.setChangedAt(clock.instant()); row.setReason(reason.trim());
        periods.saveAndFlush(row);
        var event = new AttendancePeriodHistory(); event.recordAudit(actor.getName(), clock.instant());
        event.setMonth(value); event.setPeriodVersion(row.getVersion()); event.setClosed(close);
        event.setReason(reason.trim()); event.setReportJson(row.getReportJson()); history.save(event);
        return status(row);
    }
    private Status status(AttendancePeriod p) {
        return new Status(p.getMonth(), p.getVersion(), p.isClosed(), p.getActorUserId(), p.getChangedAt(), p.getReason());
    }
}
