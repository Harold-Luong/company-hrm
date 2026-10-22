package com.company.attendance.service;

import com.company.attendance.exception.ExceptionMessages;
import com.company.attendance.dto.*;
import com.company.attendance.dto.AttendanceRequestModels.*;
import com.company.attendance.entity.*;
import com.company.attendance.enums.*;
import com.company.attendance.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import static com.company.attendance.service.ApiRules.*;
import static com.company.attendance.enums.AttendanceRequestStatus.*;
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class AttendanceRequestService {
    private final AttendanceRequestRepository requests;
    private final AttendanceRequestHistoryRepository history;
    private final ScheduleStateRepository state;
    private final ScheduleService schedules;
    private final ShiftService shifts;
    private final WorkforceClient source;
    private final AttendanceDayRepository days;
    private final OperationService operations;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final PeriodStore periods;
    private final com.company.attendance.notifications.NotificationOutbox notifications;

    public PageResponse<Response> list(boolean inbox, AttendanceRequestStatus status, int page, int size, JwtAuthenticationToken actor) {
        page(page,size); if(inbox) requireReviewer(actor);
        var result = requests.search(inbox ? null : employee(actor), status,
            PageRequest.of(page,size,Sort.by(Sort.Order.desc("createdAt"),Sort.Order.desc("id"))));
        return PageResponse.from(result.map(this::response));
    }
    public PendingCount pendingCount(JwtAuthenticationToken actor) {
        requireReviewer(actor);
        return new PendingCount(requests.countByStatus(PENDING));
    }
    public Response get(UUID id, JwtAuthenticationToken actor) { return response(read(id,actor)); }
    public PageResponse<AttendanceRequestHistory> history(UUID id,int page,int size,JwtAuthenticationToken actor) {
        read(id,actor); page(page,size);
        return PageResponse.from(history.findByRequestIdOrderByRequestVersionDesc(id,PageRequest.of(page,size)));
    }
    @Transactional
    public Response create(Write body,String key,JwtAuthenticationToken actor) {
        state.lockState();
        Response replay=operations.replay(actor.getName(),"CREATE_ATTENDANCE_REQUEST",key,body,Response.class);
        if(replay!=null) return replay;
        var row=new AttendanceRequest(); row.setId(UUID.randomUUID()); row.setEmployeeId(employee(actor));
        row.setRequesterUserId(actor.getName()); row.setStatus(PENDING); row.setActiveSlot("ACTIVE"); row.setCreatedAt(clock.instant());
        populate(row,body,actor); requests.saveAndFlush(row); audit(row,"SUBMITTED",actor);
        var result=response(row); operations.save(actor.getName(),"CREATE_ATTENDANCE_REQUEST",key,body,result); return result;
    }
    @Transactional
    public Response update(UUID id,Write body,String match,JwtAuthenticationToken actor) {
        state.lockState(); var row=read(id,actor); periods.requireOpen(row.getWorkDate()); ownPending(row,actor); requireVersion(match,row.getVersion());
        // Do not allow an expired pending request to be repurposed as a new request.
        if(row.getWorkDate().isBefore(LocalDate.now(clock))) throw error(HttpStatus.CONFLICT,ExceptionMessages.PAST_REQUEST_NOT_EDITABLE);
        var before=response(row); populate(row,body,actor);
        if(before.equals(response(row))) return before;
        // Force a version even when the injected clock has not advanced.
        row.setUpdatedAt(nextUpdate(row)); requests.flush(); audit(row,"UPDATED",actor); return response(row);
    }
    @Transactional
    public Response cancel(UUID id,String match,JwtAuthenticationToken actor) {
        state.lockState(); var row=read(id,actor); periods.requireOpen(row.getWorkDate()); ownPending(row,actor); requireVersion(match,row.getVersion());
        row.setStatus(CANCELLED); row.setActiveSlot(null); row.setUpdatedAt(nextUpdate(row)); requests.flush(); audit(row,"CANCELLED",actor); return response(row);
    }
    @Transactional
    public Response decide(UUID id,Decision decision,String match,JwtAuthenticationToken actor) {
        requireReviewer(actor); state.lockState(); var row=read(id,actor); periods.requireOpen(row.getWorkDate()); requireVersion(match,row.getVersion());
        if(row.getEmployeeId().equals(employee(actor)) || row.getRequesterUserId().equals(actor.getName()))
            throw error(HttpStatus.FORBIDDEN,ExceptionMessages.ATTENDANCE_SELF_REVIEW_FORBIDDEN);
        if(row.getStatus()!=PENDING) throw error(HttpStatus.CONFLICT,ExceptionMessages.REQUEST_NOT_PENDING_REVIEW);
        if(decision.status()!=APPROVED && decision.status()!=REJECTED) throw error(HttpStatus.BAD_REQUEST,ExceptionMessages.INVALID_REVIEW_DECISION);
        String note=decision.reviewNote()==null ? null : decision.reviewNote().trim();
        if(decision.status()==REJECTED && (note==null || note.isBlank())) throw error(HttpStatus.BAD_REQUEST,ExceptionMessages.REJECTION_REASON_REQUIRED);
        if(decision.status()==APPROVED) validateSchedule(row,new Write(row.getWorkDate(),row.getShiftId(),row.getShiftVersion(),row.getRequestType(),row.getPeriod(),row.getExpectedTime(),row.getReason()),actor,false);
        row.setStatus(decision.status()); row.setActiveSlot(decision.status()==APPROVED ? "ACTIVE" : null);
        row.recordReview(actor.getName(), clock.instant(), note); row.setUpdatedAt(nextUpdate(row));
        requests.flush(); audit(row,decision.status().name(),actor); return response(row);
    }
    private void populate(AttendanceRequest row,Write body,JwtAuthenticationToken actor) {
        periods.requireOpen(body.workDate());
        var person=source.employee(row.getEmployeeId(),actor);
        if(!source.eligible(person,body.workDate())) throw error(HttpStatus.CONFLICT,ExceptionMessages.EMPLOYEE_INELIGIBLE_ON_REQUEST_DATE);
        var plan=validateSchedule(row,body,actor,true);
        var interval=plan.intervals().stream().filter(i->i.period()==body.period()).findFirst().orElseThrow();
        row.setEmployeeCode(person.employeeCode()); row.setEmployeeName(person.lastName()+" "+person.firstName());
        row.setWorkDate(body.workDate()); row.setShiftRevision(shifts.revision(body.shiftId(), body.shiftVersion())); row.setRequestType(body.requestType()); row.setPeriod(body.period());
        row.setExpectedTime(body.expectedTime()); row.setReason(body.reason().trim());
        row.setRequestedMinutes((int)(body.requestType()==AttendanceRequestType.LATE_ARRIVAL
            ? Duration.between(ShiftTimes.at(body.workDate(),plan,interval.start()),ShiftTimes.at(body.workDate(),plan,body.expectedTime())).toMinutes() : Duration.between(ShiftTimes.at(body.workDate(),plan,body.expectedTime()),ShiftTimes.at(body.workDate(),plan,interval.end())).toMinutes()));
        row.setUpdatedAt(clock.instant());
    }
    private ShiftRequest validateSchedule(AttendanceRequest row,Write body,JwtAuthenticationToken actor,boolean newOrEdit) {
        if(newOrEdit && (body.workDate().isBefore(LocalDate.now(clock)) || body.workDate().isAfter(LocalDate.now(clock).plusDays(365))))
            throw error(HttpStatus.BAD_REQUEST,ExceptionMessages.INVALID_REQUEST_DATE);
        var recorded=days.findByEmployeeIdAndWorkDate(row.getEmployeeId(),body.workDate());
        UUID shiftId; long version; ShiftRequest plan;
        if(recorded.isPresent()) { var d=recorded.get(); shiftId=d.getShiftId(); version=d.getShiftVersion(); plan=shifts.definition(d.getShiftDefinition()); }
        else { var rule=schedules.effective(row.getEmployeeId(),body.workDate()).orElseThrow(()->error(HttpStatus.CONFLICT,ExceptionMessages.REQUEST_SCHEDULE_MISSING));
            shiftId=rule.getShiftId(); version=rule.getShiftVersion(); plan=shifts.definition(rule.getDefinition()); }
        if(!shiftId.equals(body.shiftId()) || version!=body.shiftVersion()) throw error(HttpStatus.PRECONDITION_FAILED,ExceptionMessages.REQUEST_SCHEDULE_CHANGED);
        var interval=plan.intervals().stream().filter(i->i.period()==body.period()).findFirst()
            .orElseThrow(()->error(HttpStatus.BAD_REQUEST,ExceptionMessages.REQUEST_PERIOD_OUTSIDE_SHIFT));
        if(body.expectedTime().getSecond()!=0 || body.expectedTime().getNano()!=0 || !ShiftTimes.at(body.workDate(),plan,body.expectedTime()).isAfter(ShiftTimes.at(body.workDate(),plan,interval.start())) || !ShiftTimes.at(body.workDate(),plan,body.expectedTime()).isBefore(ShiftTimes.at(body.workDate(),plan,interval.end())))
            throw error(HttpStatus.BAD_REQUEST,ExceptionMessages.INVALID_EXPECTED_TIME);
        if(!source.holidays(body.workDate(),body.workDate(),actor).isEmpty()) throw error(HttpStatus.CONFLICT,ExceptionMessages.REQUEST_ON_HOLIDAY);
        if(source.leaves(row.getEmployeeId(),body.workDate(),body.workDate(),actor).stream().anyMatch(l->
            ("APPROVED".equals(l.status()) || "PENDING".equals(l.status())) && ("FULL_DAY".equals(l.period()) || body.period().name().equals(l.period()))))
            throw error(HttpStatus.CONFLICT,ExceptionMessages.LEAVE_COVERS_REQUEST_PERIOD);
        for(var other:requests.findByEmployeeIdAndWorkDateAndActiveSlot(row.getEmployeeId(),body.workDate(),"ACTIVE")) {
            if(other.getId().equals(row.getId()) || other.getPeriod()!=body.period()) continue;
            if(other.getRequestType()==body.requestType()) throw error(HttpStatus.CONFLICT,ExceptionMessages.DUPLICATE_ATTENDANCE_REQUEST);
            LocalTime late=body.requestType()==AttendanceRequestType.LATE_ARRIVAL ? body.expectedTime() : other.getExpectedTime();
            LocalTime early=body.requestType()==AttendanceRequestType.EARLY_DEPARTURE ? body.expectedTime() : other.getExpectedTime();
            if(!ShiftTimes.at(body.workDate(),plan,late).isBefore(ShiftTimes.at(body.workDate(),plan,early))) throw error(HttpStatus.CONFLICT,ExceptionMessages.REQUESTS_COVER_ENTIRE_INTERVAL);
        }
        return plan;
    }
    private Instant nextUpdate(AttendanceRequest row) { return clock.instant().isAfter(row.getUpdatedAt()) ? clock.instant() : row.getUpdatedAt().plusNanos(1000); }
    private AttendanceRequest read(UUID id,JwtAuthenticationToken actor) {
        var row=requests.findById(id).orElseThrow(()->error(HttpStatus.NOT_FOUND,ExceptionMessages.ATTENDANCE_REQUEST_NOT_FOUND));
        if(!row.getEmployeeId().equals(employee(actor)) && !reviewer(actor)) throw error(HttpStatus.NOT_FOUND,ExceptionMessages.ATTENDANCE_REQUEST_NOT_FOUND); return row;
    }
    private void ownPending(AttendanceRequest row,JwtAuthenticationToken actor) {
        if(!row.getEmployeeId().equals(employee(actor))) throw error(HttpStatus.FORBIDDEN,ExceptionMessages.REQUEST_OWNER_REQUIRED);
        if(row.getStatus()!=PENDING) throw error(HttpStatus.CONFLICT,ExceptionMessages.REQUEST_NOT_PENDING_EDIT);
    }
    private Response response(AttendanceRequest row) {
        return new Response(row.getId(),row.getVersion(),row.getEmployeeId(),row.getEmployeeCode(),row.getEmployeeName(),row.getWorkDate(),row.getShiftId(),row.getShiftVersion(),
            shifts.definition(row.getShiftDefinition()),row.getRequestType(),row.getPeriod(),row.getExpectedTime(),row.getRequestedMinutes(),
            AttendanceCalculator.round15(row.getRequestedMinutes()*60L),row.getReason(),row.getStatus(),row.getReviewNote(),row.getCreatedAt(),row.getUpdatedAt(),row.getReviewedBy(),row.getReviewedAt());
    }
    private void audit(AttendanceRequest row,String action,JwtAuthenticationToken actor) {
        var event=new AttendanceRequestHistory(); event.recordAudit(actor.getName(), clock.instant()); event.setRequestId(row.getId()); event.setRequestVersion(row.getVersion());
        event.setAction(action); event.setSnapshot(mapper.writeValueAsString(response(row))); history.save(event);
        notifications.append(row.getId(), row.getVersion(), row.getRequesterUserId(), row.getEmployeeId(), actor.getName(), action);
    }
}
