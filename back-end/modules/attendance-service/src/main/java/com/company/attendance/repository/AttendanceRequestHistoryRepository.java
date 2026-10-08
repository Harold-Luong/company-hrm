package com.company.attendance.repository;
import com.company.attendance.entity.AttendanceRequestHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
import java.util.UUID;
public interface AttendanceRequestHistoryRepository extends JpaRepository<AttendanceRequestHistory, UUID> {
    Page<AttendanceRequestHistory> findByRequestIdOrderByRequestVersionDesc(UUID request, Pageable page);
}
