package com.company.attendance.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shift_revisions", uniqueConstraints = @UniqueConstraint(columnNames = {"shift_id", "shift_version"}))
@Getter @Setter
public class ShiftRevision {
    @Id private UUID id;
    @Column(nullable = false) private UUID shiftId;
    @Column(nullable = false) private long shiftVersion;
    @Column(nullable = false, columnDefinition = "text") private String definition;
    @Column(nullable = false) private boolean active;
    @Column(nullable = false, length = 255) private String actorUserId;
    @Column(nullable = false) private Instant occurredAt;
}
