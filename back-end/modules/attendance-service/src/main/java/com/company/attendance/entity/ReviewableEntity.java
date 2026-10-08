package com.company.attendance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Lớp cha dùng chung thông tin tạo và xét duyệt cho đơn đi trễ/về sớm và OT.
 * Kế thừa mã bản ghi và version; trạng thái, quyền duyệt và kiểm tra nghiệp vụ do từng service quản lý.
 * Không tạo bảng riêng trong database.
 */
@MappedSuperclass
@Getter
@Setter
public abstract class ReviewableEntity extends VersionedEntity {
    @Column(nullable = false)
    private Instant createdAt;

    /** Mã tài khoản xét duyệt; chưa có giá trị khi đơn chưa được xét duyệt. */
    @Column(length = 255)
    private String reviewedBy;

    private Instant reviewedAt;

    @Column(length = 1000)
    private String reviewNote;

    /** Ghi thông tin người duyệt, thời điểm và ghi chú sau khi service đã kiểm tra quyền và nghiệp vụ. */
    public void recordReview(String actor, Instant time, String note) {
        reviewedBy = actor;
        reviewedAt = time;
        reviewNote = note;
    }
}
