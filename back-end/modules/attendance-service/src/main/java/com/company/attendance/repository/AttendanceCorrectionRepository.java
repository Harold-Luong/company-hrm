package com.company.attendance.repository;

import com.company.attendance.entity.AttendanceCorrection;
import com.company.attendance.enums.AttendanceRequestStatus;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
import java.time.LocalDate;
import java.util.*;

public interface AttendanceCorrectionRepository extends JpaRepository<AttendanceCorrection, UUID> {
    @Override @EntityGraph(attributePaths = "shiftRevision")
    Optional<AttendanceCorrection> findById(UUID id);
    @EntityGraph(attributePaths = "shiftRevision")
    @Query("select r from AttendanceCorrection r where (:owner is null or r.employeeId=:owner) and (:status is null or r.status=:status)")
    Page<AttendanceCorrection> search(UUID owner, AttendanceRequestStatus status, Pageable page);
    boolean existsByEmployeeIdAndWorkDateAndActiveSlotAndIdNot(UUID employee, LocalDate date, String slot, UUID id);
}
