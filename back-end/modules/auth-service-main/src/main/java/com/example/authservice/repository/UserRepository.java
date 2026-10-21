package com.example.authservice.repository;

import com.example.authservice.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.example.authservice.enums.UserRole;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, Long> {
    @Query("""
            select u from User u where
            (:search = '' or locate(lower(:search), lower(u.email)) > 0)
            and (:role is null or :role member of u.roles)
            and (:status is null
                or (:status = 'ACTIVE' and u.active = true and u.activationPending = false and u.administrativelyLocked = false)
                or (:status = 'PENDING_ACTIVATION' and u.activationPending = true and u.administrativelyLocked = false)
                or (:status = 'DISABLED' and (u.administrativelyLocked = true or (u.active = false and u.activationPending = false))))
            """)
    Page<User> searchAccounts(@Param("search") String search, @Param("role") UserRole role,
            @Param("status") String status, Pageable pageable);
    boolean existsByEmail(String email);

    boolean existsByEmployeeId(UUID employeeId);

    Optional<User> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.email = :email")
    Optional<User> findByEmailForUpdate(@Param("email") String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

}
