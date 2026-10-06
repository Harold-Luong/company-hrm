package com.company.attendance.repository;

import com.company.attendance.entity.ShiftRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.UUID;

public interface ShiftRevisionRepository extends JpaRepository<ShiftRevision, UUID> {
    Page<ShiftRevision> findByShiftIdOrderByShiftVersionDesc(UUID id, Pageable pageable);
}
