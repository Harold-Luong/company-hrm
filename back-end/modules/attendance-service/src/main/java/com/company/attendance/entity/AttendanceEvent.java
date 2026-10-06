package com.company.attendance.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "attendance_events")
@Getter @Setter
public class AttendanceEvent {
    @Id private UUID id;
    @Column(nullable = false) private UUID dayId;
    @Column(nullable = false, length = 30) private String eventType;
    @Column(nullable = false) private Instant eventAt;
    @Column(nullable = false, length = 30) private String method;
    @Column(nullable = false, length = 255) private String actorUserId;
    @Column(nullable = false, length = 100) private String sourceIp;
}
