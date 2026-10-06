package com.company.attendance.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "attendance_schedule_batches")
@Getter @Setter
public class ScheduleBatch {
    @Id private UUID id;
    @Column(nullable = false) private long scheduleRevision;
    @Column(nullable = false, length = 255) private String actorUserId;
    @Column(nullable = false, columnDefinition = "text") private String requestBody;
    @Column(nullable = false, columnDefinition = "text") private String replacedRules;
    @Column(nullable = false) private Instant occurredAt;
}
