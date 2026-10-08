package com.company.attendance.repository;

import com.company.attendance.entity.AttendanceDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.time.LocalDate;
import java.util.*;

public interface AttendanceDayRepository extends JpaRepository<AttendanceDay, UUID> {
    @EntityGraph(attributePaths = "shiftRevision")
    Optional<AttendanceDay> findByEmployeeIdAndWorkDate(UUID employee, LocalDate date);
    @EntityGraph(attributePaths = "shiftRevision")
    List<AttendanceDay> findByWorkDateBetween(LocalDate from, LocalDate until);
    @EntityGraph(attributePaths = "shiftRevision")
    List<AttendanceDay> findByEmployeeIdAndWorkDateBetweenOrderByWorkDate(UUID employee, LocalDate from, LocalDate until);
}
