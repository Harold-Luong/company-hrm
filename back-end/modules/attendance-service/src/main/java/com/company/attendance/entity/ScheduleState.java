package com.company.attendance.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Hàng điều phối duy nhất trong database, dùng khóa để tuần tự hóa các thao tác ghi có thể xung đột.
 * Các luồng phân công, chấm công và xét duyệt dùng chung khóa này để tránh kiểm tra trên dữ liệu đang bị thay đổi.
 * Revision biểu thị phiên bản lịch toàn hệ thống; version phục vụ khóa lạc quan của JPA.
 */
@Entity
@Table(name = "attendance_schedule_state")
@Getter @Setter
public class ScheduleState {
    /** Luôn bằng 1: toàn hệ thống dùng một hàng điều phối. */
    @Id private Integer id;
    @Version private long version;
    /** Tăng khi áp dụng một đợt phân công, dùng kiểm tra phiên bản lịch xem trước khi áp dụng. */
    @Column(nullable = false) private long revision;
}
