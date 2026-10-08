package com.company.attendance.service;
import com.company.attendance.dto.*;
import com.company.attendance.entity.AttendanceRequest;
import com.company.attendance.enums.*;
import com.company.attendance.repository.AttendanceRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;
import static java.util.stream.Collectors.groupingBy;
@Service @RequiredArgsConstructor
public class AttendancePermissionService {
    private final AttendanceRequestRepository requests;
    public Map<LocalDate, List<AttendanceRequest>> activeRequests(UUID employee, LocalDate from, LocalDate until) {
        return requests.findByEmployeeIdAndWorkDateBetweenAndActiveSlot(employee, from, until, "ACTIVE")
                .stream().collect(groupingBy(AttendanceRequest::getWorkDate));
    }

    public AttendancePermissionCoverage coverage(LocalDate date, UUID shiftId, long shiftVersion,
            ShiftRequest shift, Instant in, Instant out, AttendanceCalculator.Result result,
            List<WorkforceClient.Leave> leaves, List<WorkforceClient.Holiday> holidays, List<AttendanceRequest> rows) {
        List<UUID> pending=new ArrayList<>(),approved=new ArrayList<>(),conflicts=new ArrayList<>();
        int late=0,early=0; long coveredLate=0,coveredEarly=0; Instant reviewed=null;
        for(var r:rows) {
            if(r.getStatus()==AttendanceRequestStatus.PENDING) { pending.add(r.getId()); continue; }
            if(reviewed==null || (r.getReviewedAt()!=null && r.getReviewedAt().isAfter(reviewed))) reviewed=r.getReviewedAt();
            var interval=shift.intervals().stream().filter(i->i.period()==r.getPeriod()).findFirst();
            boolean leaveConflict=leaves.stream().anyMatch(l->"APPROVED".equals(l.status()) && !l.startDate().isAfter(date) && !l.endDate().isBefore(date)
                && ("FULL_DAY".equals(l.period()) || r.getPeriod().name().equals(l.period())));
            boolean holiday=holidays.stream().anyMatch(h->!h.startDate().isAfter(date) && !h.endDate().isBefore(date));
            if(!r.getShiftId().equals(shiftId) || r.getShiftVersion()!=shiftVersion || interval.isEmpty() || leaveConflict || holiday) { conflicts.add(r.getId()); continue; }
            approved.add(r.getId());
            boolean isLate=r.getRequestType()==AttendanceRequestType.LATE_ARRIVAL;
            if(isLate) late+=r.getRequestedMinutes(); else early+=r.getRequestedMinutes();
            if(in==null || out==null || result.actualSeconds()==null) continue;
            var i=interval.get();
            Instant start=ShiftTimes.at(date,shift,i.start()),end=ShiftTimes.at(date,shift,i.end());
            long missing=isLate ? Math.max(0,Duration.between(start,in.isBefore(end)?in:end).getSeconds())
                : Math.max(0,Duration.between(out.isAfter(start)?out:start,end).getSeconds());
            long covered=Math.min(missing,r.getRequestedMinutes()*60L);
            if(isLate) coveredLate+=covered; else coveredEarly+=covered;
        }
        boolean known=result.lateSeconds()!=null && result.earlySeconds()!=null && result.actualSeconds()!=null;
        return new AttendancePermissionCoverage(pending,approved,conflicts,late,early,known?coveredLate:null,known?coveredEarly:null,
            known?AttendanceCalculator.round15(result.lateSeconds()-coveredLate):null,
            known?AttendanceCalculator.round15(result.earlySeconds()-coveredEarly):null,reviewed);
    }
}
