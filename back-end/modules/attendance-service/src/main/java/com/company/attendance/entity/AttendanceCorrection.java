package com.company.attendance.entity;

import com.company.attendance.enums.AttendanceRequestStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.*;
import java.util.UUID;

/** Proposed complete pair of times; approval never changes the original punch events. */
@Entity @Table(name = "attendance_corrections") @Getter @Setter
public class AttendanceCorrection extends ReviewableEntity {
    @Column(nullable = false) private UUID employeeId;
    @Column(nullable = false) private String requesterUserId;
    @Column(nullable = false) private String employeeCode;
    @Column(nullable = false) private String employeeName;
    @Column(nullable = false) private LocalDate workDate;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({
        @JoinColumn(name = "shift_id", referencedColumnName = "shift_id", nullable = false),
        @JoinColumn(name = "shift_version", referencedColumnName = "shift_version", nullable = false)
    })
    @JsonIgnore private ShiftRevision shiftRevision;
    private Long baseRecordVersion;
    private Instant beforeCheckIn;
    private Instant beforeCheckOut;
    @Column(nullable = false) private Instant proposedCheckIn;
    @Column(nullable = false) private Instant proposedCheckOut;
    @Column(nullable = false, length = 1000) private String reason;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private AttendanceRequestStatus status;
    /** Only PENDING occupies this slot; a later correction may replace an approved pair. */
    @Column(length = 10) private String activeSlot;
    @Column(nullable = false) private Instant updatedAt;
}
