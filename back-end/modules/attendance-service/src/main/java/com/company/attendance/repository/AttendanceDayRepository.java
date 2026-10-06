package com.company.attendance.repository;

import com.company.attendance.entity.AttendanceDay;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.*;

public interface AttendanceDayRepository extends JpaRepository<AttendanceDay, UUID> {
    Optional<AttendanceDay> findByEmployeeIdAndWorkDate(UUID employee, LocalDate date);
    List<AttendanceDay> findByWorkDateBetween(LocalDate from, LocalDate until);
    List<AttendanceDay> findByEmployeeIdAndWorkDateBetweenOrderByWorkDate(UUID employee, LocalDate from, LocalDate until);
}
