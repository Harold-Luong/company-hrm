package com.company.attendance.repository;
import com.company.attendance.entity.AttendanceCorrectionHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
import java.util.UUID;
public interface AttendanceCorrectionHistoryRepository extends JpaRepository<AttendanceCorrectionHistory, UUID> {
    Page<AttendanceCorrectionHistory> findByRequestIdOrderByRequestVersionDesc(UUID request, Pageable page);
}
