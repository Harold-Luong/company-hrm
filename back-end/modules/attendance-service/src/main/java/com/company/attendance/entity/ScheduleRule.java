package com.company.attendance.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "work_schedule_rules")
@Getter @Setter
public class ScheduleRule {
    @Id private UUID id;
    private UUID employeeId;
    @Column(nullable = false) private UUID batchId;
    @Column(nullable = false) private UUID shiftId;
    @Column(nullable = false) private long shiftVersion;
    @Column(nullable = false, columnDefinition = "text") private String definition;
    @Column(nullable = false) private int weekday;
    @Column(nullable = false) private LocalDate effectiveFrom;
    @Column(nullable = false) private LocalDate effectiveUntil;
}
