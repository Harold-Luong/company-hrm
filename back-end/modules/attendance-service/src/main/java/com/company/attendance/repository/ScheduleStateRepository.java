package com.company.attendance.repository;

import com.company.attendance.entity.ScheduleState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;

public interface ScheduleStateRepository extends JpaRepository<ScheduleState, Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ScheduleState s where s.id = 1")
    ScheduleState lockState();
}
