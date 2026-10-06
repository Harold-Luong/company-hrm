package com.company.attendance.repository;

import com.company.attendance.entity.AttendanceOperation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface AttendanceOperationRepository extends JpaRepository<AttendanceOperation, UUID> {
    Optional<AttendanceOperation> findByActorUserIdAndOperationTypeAndRequestKey(String actor, String type, String key);
}
