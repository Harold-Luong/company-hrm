package com.example.authservice.repository;

import com.example.authservice.entity.RefreshSessions;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;
import java.util.Optional;
import java.time.Instant;

public interface RefreshSessionsRepository extends JpaRepository<RefreshSessions, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from RefreshSessions session where session.uuid = :id")
    Optional<RefreshSessions> findByIdForUpdate(@Param("id") UUID id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshSessions s set s.revokedAt = :revokedAt where s.user.id = :userId and s.revokedAt is null")
    int revokeAllByUserId(@Param("userId") Long userId, @Param("revokedAt") Instant revokedAt);
}
