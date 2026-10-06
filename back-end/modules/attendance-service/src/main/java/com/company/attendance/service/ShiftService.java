package com.company.attendance.service;

import com.company.attendance.dto.*;
import com.company.attendance.entity.*;
import com.company.attendance.enums.AttendanceMode;
import com.company.attendance.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.UUID;
import static com.company.attendance.service.ApiRules.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShiftService {
    private final WorkShiftRepository shifts;
    private final ShiftRevisionRepository revisions;
    private final ScheduleStateRepository state;
    private final ObjectMapper mapper;
    private final Clock clock;

    public PageResponse<ShiftResponse> list(int page, int size) {
        page(page, size);
        return PageResponse.from(shifts.findAll(PageRequest.of(page, size, Sort.by("name", "id"))).map(this::response));
    }
    public ShiftResponse get(UUID id) { return response(require(id)); }
    public WorkShift require(UUID id) {
        return shifts.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Shift not found"));
    }
    public ShiftRequest definition(String json) { return mapper.readValue(json, ShiftRequest.class); }
    public PageResponse<ShiftRevision> history(UUID id, int page, int size) {
        require(id); page(page, size);
        return PageResponse.from(revisions.findByShiftIdOrderByShiftVersionDesc(id, PageRequest.of(page, size)));
    }
    @Transactional
    public ShiftResponse create(ShiftRequest body, JwtAuthenticationToken actor) {
        requireReviewer(actor); state.lockState();
        var shift = new WorkShift(); shift.setId(UUID.randomUUID());
        apply(shift, body); shifts.saveAndFlush(shift); audit(shift, actor);
        return response(shift);
    }
    @Transactional
    public ShiftResponse update(UUID id, ShiftRequest body, String match, JwtAuthenticationToken actor) {
        requireReviewer(actor); state.lockState();
        var shift = require(id); requireVersion(match, shift.getVersion());
        if (definition(shift.getDefinition()).equals(body)) return response(shift);
        apply(shift, body); shifts.flush(); audit(shift, actor);
        return response(shift);
    }
    @Transactional
    public ShiftResponse deactivate(UUID id, String match, JwtAuthenticationToken actor) {
        requireReviewer(actor); state.lockState();
        var shift = require(id); requireVersion(match, shift.getVersion());
        if (!shift.isActive()) throw error(HttpStatus.CONFLICT, "Shift is already inactive");
        shift.setActive(false); shift.setUpdatedAt(clock.instant()); shifts.flush(); audit(shift, actor);
        return response(shift);
    }
    private void apply(WorkShift shift, ShiftRequest body) {
        int minutes = validate(body);
        var clean = new ShiftRequest(body.name().trim(), body.mode(), body.timezone(), body.intervals(),
                body.checkInFrom(), body.checkOutUntil());
        shift.setName(clean.name()); shift.setDefinition(mapper.writeValueAsString(clean));
        shift.setRequiredMinutes(minutes); shift.setUpdatedAt(clock.instant());
    }
    public int validate(ShiftRequest body) {
        if (body.mode() != AttendanceMode.FIXED_SHIFT)
            throw error(HttpStatus.BAD_REQUEST, "Only FIXED_SHIFT is supported in this release");
        if (!"Asia/Ho_Chi_Minh".equals(body.timezone()))
            throw error(HttpStatus.BAD_REQUEST, "Only Asia/Ho_Chi_Minh is supported in this release");
        var periods = new HashSet<>(); LocalTime previous = null; int minutes = 0;
        for (var interval : body.intervals()) {
            if (!minutePrecision(interval.start()) || !minutePrecision(interval.end())
                    || !interval.start().isBefore(interval.end())
                    || (previous != null && interval.start().isBefore(previous)) || !periods.add(interval.period()))
                throw error(HttpStatus.BAD_REQUEST, "Intervals must be ordered, non-overlapping and have unique periods, at minute precision");
            previous = interval.end();
            minutes += (int) Duration.between(interval.start(), interval.end()).toMinutes();
        }
        if (!minutePrecision(body.checkInFrom()) || !minutePrecision(body.checkOutUntil())
                || body.checkInFrom().isAfter(body.intervals().getFirst().start())
                || body.checkOutUntil().isBefore(body.intervals().getLast().end()))
            throw error(HttpStatus.BAD_REQUEST, "The recording window must cover the entire same-day shift");
        return minutes;
    }
    private boolean minutePrecision(LocalTime time) { return time.getSecond() == 0 && time.getNano() == 0; }
    private ShiftResponse response(WorkShift shift) {
        return new ShiftResponse(shift.getId(), shift.getVersion(), definition(shift.getDefinition()),
                shift.getRequiredMinutes(), shift.isActive(), shift.getUpdatedAt());
    }
    private void audit(WorkShift shift, JwtAuthenticationToken actor) {
        var revision = new ShiftRevision(); revision.setId(UUID.randomUUID());
        revision.setShiftId(shift.getId()); revision.setShiftVersion(shift.getVersion());
        revision.setDefinition(shift.getDefinition()); revision.setActive(shift.isActive());
        revision.setActorUserId(actor.getName()); revision.setOccurredAt(clock.instant());
        revisions.save(revision);
    }
}
