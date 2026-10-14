package com.company.attendance.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.*;
@Entity @Table(name="attendance_periods") @Getter @Setter
public class AttendancePeriod {
    @Id @Column(name="period_month", length=7) private String month;
    @Version private long version;
    @Column(nullable=false) private boolean closed;
    @Column(nullable=false) private String actorUserId;
    @Column(nullable=false) private Instant changedAt;
    @Column(nullable=false, columnDefinition="text") private String reason;
    @Column(nullable=false, columnDefinition="text") private String reportJson;
    @Column(nullable=false, columnDefinition="text") private String sourceJson;
}
