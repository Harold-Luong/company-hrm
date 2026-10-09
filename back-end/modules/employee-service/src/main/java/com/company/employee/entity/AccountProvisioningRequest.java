package com.company.employee.entity;

import com.company.employee.enums.ProvisioningStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/** Request state; login email is independent of the employee's personal email. */
@Entity
@Table(name = "account_provisioning_requests",
        uniqueConstraints = @UniqueConstraint(name = "account_request_idempotency_key",
                columnNames = {"requested_by", "idempotency_key"}),
        indexes = @Index(name = "account_request_employee_idx", columnList = "employee_id"))
@Check(name = "account_request_pending", constraints =
        "(status = 'PENDING' AND pending_employee_id IS NOT NULL AND pending_employee_id = employee_id) "
        + "OR (status <> 'PENDING' AND pending_employee_id IS NULL)")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountProvisioningRequest {
    @Id
    @Column(name = "request_id")
    private UUID requestId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "pending_employee_id", unique = true)
    private UUID pendingEmployeeId;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "requested_by", nullable = false, length = 255)
    private String requestedBy;

    @Column(name = "idempotency_key", nullable = false)
    private UUID idempotencyKey;

    @Column(name = "correlation_id", nullable = false)
    private UUID correlationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProvisioningStatus status;

    @Column(name = "error_code", length = 80)
    private String errorCode;

    @Column(name = "result_event_id")
    private UUID resultEventId;

    @CreationTimestamp
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
