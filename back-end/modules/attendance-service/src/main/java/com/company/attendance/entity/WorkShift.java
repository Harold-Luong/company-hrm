package com.company.attendance.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "work_shifts")
@Getter @Setter
public class WorkShift {
    @Id private UUID id;
    @Version private long version;
    @Column(nullable = false, length = 100) private String name;
    @Column(nullable = false, columnDefinition = "text") private String definition;
    @Column(nullable = false) private int requiredMinutes;
    @Column(nullable = false) private boolean active = true;
    @Column(nullable = false) private Instant updatedAt;
}
