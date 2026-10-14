package com.company.attendance.repository;
import com.company.attendance.entity.AttendancePeriodHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface AttendancePeriodHistoryRepository extends JpaRepository<AttendancePeriodHistory,UUID> {
    List<AttendancePeriodHistory> findByMonthOrderByPeriodVersionDesc(String month);
}
