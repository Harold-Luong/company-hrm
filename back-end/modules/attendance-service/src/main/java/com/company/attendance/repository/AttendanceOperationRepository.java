package com.company.attendance.repository;

import com.company.attendance.entity.AttendanceOperation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttendanceOperationRepository extends JpaRepository<AttendanceOperation, UUID> {
    Optional<AttendanceOperation> findByActorUserIdAndOperationTypeAndRequestKey(String actor, String type, String key);

    @Query("select o.id from AttendanceOperation o where o.operationType in :types and o.occurredAt < :cutoff and o.responseBody is not null order by o.occurredAt, o.id")
    List<UUID> expiredResponses(List<String> types, Instant cutoff, Pageable page);

    @Modifying
    @Query("update AttendanceOperation o set o.responseBody = null where o.id in :ids")
    int clearResponses(List<UUID> ids);
}
