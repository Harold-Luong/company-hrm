package com.company.attendance.repository;

import com.company.attendance.entity.ScheduleBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ScheduleBatchRepository extends JpaRepository<ScheduleBatch, UUID> {}
