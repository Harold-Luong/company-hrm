package com.company.attendance.service;

import com.company.attendance.dto.*;
import com.company.attendance.dto.OvertimeModels.*;
import com.company.attendance.entity.OvertimeRequest;
import com.company.attendance.enums.AttendanceRequestStatus;
import com.company.attendance.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import static com.company.attendance.service.ApiRules.*;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class OvertimeService {
    private static final ZoneId ZONE=ZoneId.of("Asia/Ho_Chi_Minh");
    private final OvertimeRequestRepository requests;
    private final ScheduleStateRepository state;
    private final ScheduleService schedules;
    private final ShiftService shifts;
    private final WorkforceClient source;
    private final OperationService operations;
    private final Clock clock;

    public PageResponse<Response> list(boolean inbox,String status,int page,int size,JwtAuthenticationToken actor) {
        if(inbox) requireReviewer(actor); page(page,size);
        return PageResponse.from(requests.search(inbox?null:employee(actor),status,PageRequest.of(page,size,Sort.by("createdAt").descending())).map(this::response));
    }
    @Transactional
    public Response create(Write body,String key,JwtAuthenticationToken actor) {
        state.lockState();
        var replay=operations.replay(actor.getName(),"CREATE_OT",key,body,Response.class); if(replay!=null) return replay;
        var person=source.employee(employee(actor),actor);
        var row=new OvertimeRequest(); row.setId(UUID.randomUUID()); row.setEmployeeId(person.id());
        row.setRequesterUserId(actor.getName()); row.setEmployeeName(person.lastName()+" "+person.firstName());
        row.setStartTime(body.start()); row.setEndTime(body.end()); row.setWorkDate(body.start().toLocalDate());
        row.setReason(body.reason().trim()); row.setStatus("PENDING"); row.setCreatedAt(clock.instant());
        validate(row,actor,true); requests.saveAndFlush(row); var result=response(row);
        operations.save(actor.getName(),"CREATE_OT",key,body,result); return result;
    }
    private void validate(OvertimeRequest row,JwtAuthenticationToken actor,boolean beforeStart) {
        var start=row.getStartTime(); var end=row.getEndTime();
        if(start.getSecond()!=0 || start.getNano()!=0 || end.getSecond()!=0 || end.getNano()!=0
                || !end.isAfter(start) || Duration.between(start,end).toHours()>=24
                || (beforeStart && !start.atZone(ZONE).toInstant().isAfter(clock.instant())) || start.toLocalDate().isAfter(LocalDate.now(clock).plusDays(365)))
            throw error(HttpStatus.BAD_REQUEST,"OT must be in the future, at minute precision, and shorter than 24 hours");
        var person=source.employee(row.getEmployeeId(),actor);
        if(!source.eligible(person,start.toLocalDate()) || !source.eligible(person,end.toLocalDate()))
            throw error(HttpStatus.CONFLICT,"Employee is not eligible for OT");
        if(requests.overlapping(row.getEmployeeId(),start,end).stream().anyMatch(r->!r.getId().equals(row.getId())))
            throw error(HttpStatus.CONFLICT,"OT overlaps another pending or approved request");
        for(LocalDate date=start.toLocalDate().minusDays(1);!date.isAfter(end.toLocalDate());date=date.plusDays(1)) {
            var rule=schedules.effective(row.getEmployeeId(),date);
            var leaves=source.leaves(row.getEmployeeId(),date,date,actor);
            boolean holiday=!source.holidays(date,date,actor).isEmpty();
            if(rule.isEmpty()) {
                if(!date.isBefore(start.toLocalDate()) && leaves.stream().anyMatch(l->"APPROVED".equals(l.status()) || "PENDING".equals(l.status())))
                    throw error(HttpStatus.CONFLICT,"OT conflicts with leave");
                continue;
            }
            var plan=shifts.definition(rule.get().getDefinition());
            for(var interval:plan.intervals()) {
                boolean overlap=start.atZone(ZONE).toInstant().isBefore(ShiftTimes.at(date,plan,interval.end()))
                        && end.atZone(ZONE).toInstant().isAfter(ShiftTimes.at(date,plan,interval.start()));
                if(!overlap) continue;
                if(leaves.stream().anyMatch(l->("APPROVED".equals(l.status()) || "PENDING".equals(l.status()))
                        && ("FULL_DAY".equals(l.period()) || interval.period().name().equals(l.period()))))
                    throw error(HttpStatus.CONFLICT,"OT conflicts with leave");
                if(!holiday) throw error(HttpStatus.CONFLICT,"OT must be outside regular working intervals");
            }
        }
    }
    @Transactional
    public Response decide(UUID id,AttendanceRequestModels.Decision decision,String match,JwtAuthenticationToken actor) {
        requireReviewer(actor); state.lockState(); var row=read(id,actor); requireVersion(match,row.getVersion());
        if(row.getRequesterUserId().equals(actor.getName()) || row.getEmployeeId().equals(employee(actor)))
            throw error(HttpStatus.FORBIDDEN,"You cannot review your own OT request");
        pending(row);
        if(decision.status()!=AttendanceRequestStatus.APPROVED && decision.status()!=AttendanceRequestStatus.REJECTED)
            throw error(HttpStatus.BAD_REQUEST,"Decision must be APPROVED or REJECTED");
        if(decision.status()==AttendanceRequestStatus.REJECTED && (decision.reviewNote()==null || decision.reviewNote().isBlank()))
            throw error(HttpStatus.BAD_REQUEST,"A rejection reason is required");
        if(decision.status()==AttendanceRequestStatus.APPROVED) validate(row,actor,true);
        row.setStatus(decision.status().name()); row.recordReview(actor.getName(), clock.instant(), decision.reviewNote());
        requests.flush(); var result=response(row);
        operations.save(actor.getName(),"DECIDE_OT",UUID.randomUUID().toString(),Map.of("id",id,"decision",decision),result); return result;
    }
    @Transactional
    public Response cancel(UUID id,String match,JwtAuthenticationToken actor) {
        state.lockState(); var row=read(id,actor); own(row,actor); requireVersion(match,row.getVersion()); pending(row);
        row.setStatus("CANCELLED"); requests.flush(); var result=response(row);
        operations.save(actor.getName(),"CANCEL_OT",UUID.randomUUID().toString(),Map.of("id",id),result); return result;
    }
    @Transactional
    public Response punch(UUID id,boolean checkIn,String key,String ip,JwtAuthenticationToken actor) {
        state.lockState(); var type=checkIn?"OT_CHECK_IN":"OT_CHECK_OUT"; var body=Map.of("id",id);
        var replay=operations.replay(actor.getName(),type,key,body,Response.class); if(replay!=null) return replay;
        var row=read(id,actor); own(row,actor);
        if(!"APPROVED".equals(row.getStatus())) throw error(HttpStatus.CONFLICT,"OT must be approved before recording time");
        Instant now=clock.instant(),start=row.getStartTime().atZone(ZONE).toInstant(),end=row.getEndTime().atZone(ZONE).toInstant();
        if(checkIn) {
            if(row.getCheckIn()!=null) throw error(HttpStatus.CONFLICT,"OT already started");
            if(now.isBefore(start) || !now.isBefore(end)) throw error(HttpStatus.FORBIDDEN,"Outside the approved OT interval");
            if(!source.eligible(source.employee(row.getEmployeeId(),actor),row.getWorkDate())) throw error(HttpStatus.FORBIDDEN,"Employee is not eligible for OT");
            validate(row,actor,false);
            row.setCheckIn(now); row.setCheckInIp(ip);
        } else {
            if(row.getCheckIn()==null || row.getCheckOut()!=null || !now.isAfter(row.getCheckIn())) throw error(HttpStatus.CONFLICT,"OT check-in is required and check-out must be unique");
            row.setCheckOut(now); row.setCheckOutIp(ip);
        }
        requests.flush(); var result=response(row); operations.save(actor.getName(),type,key,body,result); return result;
    }
    public Summary summary(UUID employee,LocalDate date) {
        return summarize(requests.findByEmployeeIdAndWorkDate(employee, date));
    }

    public Map<LocalDate, Summary> summaries(UUID employee, LocalDate from, LocalDate until) {
        return requests.findByEmployeeIdAndWorkDateBetween(employee, from, until).stream()
                .collect(java.util.stream.Collectors.groupingBy(OvertimeRequest::getWorkDate,
                        java.util.stream.Collectors.collectingAndThen(java.util.stream.Collectors.toList(), this::summarize)));
    }

    private Summary summarize(List<OvertimeRequest> rows) {
        int approved=0,counted=0; List<UUID> pending=new ArrayList<>(),ids=new ArrayList<>();
        for(var row:rows) {
            if("PENDING".equals(row.getStatus())) pending.add(row.getId());
            if("APPROVED".equals(row.getStatus())) {
                ids.add(row.getId()); approved+=response(row).approvedMinutes();
                var minutes=counted(row); if(minutes!=null) counted+=minutes;
            }
        }
        return new Summary(approved,counted,pending,ids);
    }
    private Integer counted(OvertimeRequest row) {
        if(!"APPROVED".equals(row.getStatus())) return 0;
        if(row.getCheckIn()==null || row.getCheckOut()==null) return null;
        var start=row.getStartTime().atZone(ZONE).toInstant(); var end=row.getEndTime().atZone(ZONE).toInstant();
        return (int)Math.max(0,Duration.between(row.getCheckIn().isAfter(start)?row.getCheckIn():start,row.getCheckOut().isBefore(end)?row.getCheckOut():end).toMinutes());
    }
    private Response response(OvertimeRequest row) {
        return new Response(row.getId(),row.getVersion(),row.getEmployeeId(),row.getEmployeeName(),row.getStartTime(),row.getEndTime(),row.getReason(),row.getStatus(),row.getReviewNote(),row.getReviewedBy(),row.getReviewedAt(),row.getCreatedAt(),row.getCheckIn(),row.getCheckOut(),
                "APPROVED".equals(row.getStatus())?(int)Duration.between(row.getStartTime(),row.getEndTime()).toMinutes():0,counted(row));
    }
    private OvertimeRequest read(UUID id,JwtAuthenticationToken actor) {
        var row=requests.findById(id).orElseThrow(()->error(HttpStatus.NOT_FOUND,"OT request not found"));
        if(!row.getEmployeeId().equals(employee(actor)) && !reviewer(actor)) throw error(HttpStatus.NOT_FOUND,"OT request not found"); return row;
    }
    private void own(OvertimeRequest row,JwtAuthenticationToken actor) {
        if(!row.getEmployeeId().equals(employee(actor))) throw error(HttpStatus.FORBIDDEN,"Only the owner may record or cancel OT");
    }
    private void pending(OvertimeRequest row) {
        if(!"PENDING".equals(row.getStatus())) throw error(HttpStatus.CONFLICT,"Only pending OT requests can be changed");
    }
}
