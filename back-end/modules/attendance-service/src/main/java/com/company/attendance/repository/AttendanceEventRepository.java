package com.company.attendance.repository;

import com.company.attendance.entity.AttendanceEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface AttendanceEventRepository extends JpaRepository<AttendanceEvent, UUID> {
    List<AttendanceEvent> findByDayIdOrderByEventAt(UUID dayId);
}
