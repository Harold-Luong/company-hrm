package com.company.attendance.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

/**
 * Mẫu ca làm việc do HR/ADMIN cấu hình: tên ca, các khoảng làm việc và cửa sổ cho phép chấm công.
 * Lưu định nghĩa hiện tại; lịch sử các phiên bản nằm trong ShiftRevision.
 * Mẫu ca cần được áp dụng qua ScheduleRule để trở thành lịch làm việc của nhân viên.
 */
@Entity
@Table(name = "work_shifts")
@Getter @Setter
public class WorkShift extends VersionedEntity {
    @Column(nullable = false, length = 100) private String name;
    /** JSON chứa chế độ chấm công, múi giờ, các buổi, cửa sổ ghi nhận và cờ ca qua đêm. */
    @Column(nullable = false, columnDefinition = "text") private String definition;
    /** Tổng số phút làm việc của mẫu ca, không bao gồm khoảng nghỉ giữa các buổi. */
    @Column(nullable = false) private int requiredMinutes;
    /** false là ngừng cho phép phân công mới, vẫn giữ mẫu ca và lịch sử cũ. */
    @Column(nullable = false) private boolean active = true;
    @Column(nullable = false) private Instant updatedAt;
}
