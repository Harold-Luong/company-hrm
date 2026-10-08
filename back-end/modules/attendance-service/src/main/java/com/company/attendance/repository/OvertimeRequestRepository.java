package com.company.attendance.repository;
import com.company.attendance.entity.OvertimeRequest;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
import java.time.*;
import java.util.*;
public interface OvertimeRequestRepository extends JpaRepository<OvertimeRequest,UUID> {
    @Query("select r from OvertimeRequest r where (:owner is null or r.employeeId=:owner) and (:status is null or r.status=:status)")
    Page<OvertimeRequest> search(UUID owner,String status,Pageable page);
    List<OvertimeRequest> findByEmployeeIdAndWorkDate(UUID employee,LocalDate date);
    List<OvertimeRequest> findByEmployeeIdAndWorkDateBetween(UUID employee, LocalDate from, LocalDate until);
    @Query("select r from OvertimeRequest r where r.employeeId=:employee and r.status in ('PENDING','APPROVED') and r.startTime < :until and r.endTime > :from")
    List<OvertimeRequest> overlapping(UUID employee,LocalDateTime from,LocalDateTime until);
    List<OvertimeRequest> findByStatusAndWorkDateBetween(String status,LocalDate from,LocalDate until);
}
