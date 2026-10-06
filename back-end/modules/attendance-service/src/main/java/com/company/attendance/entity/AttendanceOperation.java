package com.company.attendance.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "attendance_operations", uniqueConstraints = @UniqueConstraint(columnNames = {"actor_user_id", "operation_type", "request_key"}))
@Getter @Setter
public class AttendanceOperation {
    @Id private UUID id;
    @Column(nullable = false, length = 255) private String actorUserId;
    @Column(nullable = false, length = 50) private String operationType;
    @Column(nullable = false, length = 100) private String requestKey;
    @Column(nullable = false, columnDefinition = "text") private String requestBody;
    @Column(nullable = false, columnDefinition = "text") private String responseBody;
    @Column(nullable = false) private Instant occurredAt;
}
