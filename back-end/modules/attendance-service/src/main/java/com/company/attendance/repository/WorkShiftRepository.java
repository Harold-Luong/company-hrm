package com.company.attendance.repository;

import com.company.attendance.entity.WorkShift;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface WorkShiftRepository extends JpaRepository<WorkShift, UUID> {}
