package com.company.attendance.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
@Entity @Table(name="attendance_period_history") @Getter @Setter
public class AttendancePeriodHistory extends AuditRecord {
    @Column(name="period_month", nullable=false, length=7) private String month;
    @Column(nullable=false) private long periodVersion;
    @Column(nullable=false) private boolean closed;
    @Column(nullable=false, columnDefinition="text") private String reason;
    @Column(nullable=false, columnDefinition="text") private String reportJson;
}
