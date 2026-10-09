package com.company.attendance.repository;

import com.company.attendance.entity.AttendanceRequest;
import com.company.attendance.enums.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
import java.time.LocalDate;
import java.util.*;

public interface AttendanceRequestRepository extends JpaRepository<AttendanceRequest, UUID> {
    @Override
    @EntityGraph(attributePaths = "shiftRevision")
    Optional<AttendanceRequest> findById(UUID id);

    @Query("select r from AttendanceRequest r where (:owner is null or r.employeeId=:owner) and (:status is null or r.status=:status)")
    @EntityGraph(attributePaths = "shiftRevision")
    Page<AttendanceRequest> search(UUID owner, AttendanceRequestStatus status, Pageable page);

    @EntityGraph(attributePaths = "shiftRevision")
    List<AttendanceRequest> findByEmployeeIdAndWorkDateAndActiveSlot(UUID employee, LocalDate date, String activeSlot);

    @EntityGraph(attributePaths = "shiftRevision")
    List<AttendanceRequest> findByEmployeeIdAndWorkDateBetweenAndActiveSlot(UUID employee, LocalDate from,
            LocalDate until, String activeSlot);

    @EntityGraph(attributePaths = "shiftRevision")
    List<AttendanceRequest> findByStatusAndWorkDateBetween(AttendanceRequestStatus status, LocalDate from,
            LocalDate until);
}
