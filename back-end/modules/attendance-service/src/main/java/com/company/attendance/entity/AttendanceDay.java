package com.company.attendance.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "attendance_daily", uniqueConstraints = @UniqueConstraint(columnNames = {"employee_id", "work_date"}))
@Getter @Setter
public class AttendanceDay {
    @Id private UUID id;
    @Version private long version;
    @Column(nullable = false) private UUID employeeId;
    @Column(nullable = false) private LocalDate workDate;
    @Column(nullable = false) private UUID shiftId;
    @Column(nullable = false) private long shiftVersion;
    @Column(nullable = false, columnDefinition = "text") private String shiftDefinition;
    @Column(nullable = false, columnDefinition = "text") private String employeeSnapshot;
    @Column(nullable = false, columnDefinition = "text") private String leaveSnapshot;
    @Column(nullable = false, columnDefinition = "text") private String holidaySnapshot;
    @Column(nullable = false) private Instant sourceObservedAt;
    private Instant checkIn;
    private Instant checkOut;
}
