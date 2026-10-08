package com.company.attendance.repository;

import com.company.attendance.entity.ShiftRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.UUID;
import java.util.Optional;

public interface ShiftRevisionRepository extends JpaRepository<ShiftRevision, UUID> {
    Optional<ShiftRevision> findByShiftIdAndShiftVersion(UUID shiftId, long shiftVersion);
    Page<ShiftRevision> findByShiftIdOrderByShiftVersionDesc(UUID id, Pageable pageable);
}
