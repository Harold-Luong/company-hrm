package com.company.attendance.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** A database mutex serializes schedule changes and the creation of attendance snapshots. */
@Entity
@Table(name = "attendance_schedule_state")
@Getter @Setter
public class ScheduleState {
    @Id private Integer id;
    @Version private long version;
    @Column(nullable = false) private long revision;
}
