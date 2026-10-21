package com.company.attendance.repository;

import com.company.attendance.entity.NotificationOutboxEvent;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutboxEvent, UUID> {
    // The publisher must hold this row lock until its status update commits.
    @Transactional(propagation = Propagation.MANDATORY)
    @Query(value = """
            SELECT * FROM notification_outbox
            WHERE sent_at IS NULL AND next_attempt_at <= :now
            ORDER BY created_at, event_id LIMIT 1 FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<NotificationOutboxEvent> findNextPendingForUpdate(@Param("now") Instant now);
}
