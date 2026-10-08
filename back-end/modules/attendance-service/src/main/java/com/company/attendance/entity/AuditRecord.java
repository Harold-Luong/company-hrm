package com.company.attendance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Lớp cha dùng chung mã bản ghi, người thao tác và thời điểm cho các bản ghi audit.
 * Không tạo bảng riêng; entity kế thừa lưu các trường này trong bảng của mình và giữ các khóa ngoại riêng.
 */
@MappedSuperclass
@Getter
@Setter
public abstract class AuditRecord {
    @Id
    private UUID id;

    /** Mã tài khoản đã xác thực hoặc định danh hệ thống khi khởi tạo tự động. */
    @Column(nullable = false, length = 255, updatable = false)
    private String actorUserId;

    @Column(nullable = false, updatable = false)
    private Instant occurredAt;

    /** Khởi tạo audit mới bằng người thao tác và thời gian từ Clock của service để nhất quán và dễ kiểm thử. */
    public void recordAudit(String actor, Instant time) {
        actorUserId = Objects.requireNonNull(actor);
        occurredAt = Objects.requireNonNull(time);
        id = UUID.randomUUID();
    }
}
