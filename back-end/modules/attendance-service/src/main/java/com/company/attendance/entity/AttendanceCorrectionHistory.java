package com.company.attendance.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;
/**
 * Lưu lịch sử thay đổi của đơn bổ sung/điều chỉnh chấm công: gửi, sửa, rút hoặc xét duyệt.
 * Mỗi bản ghi chứa hành động và bản chụp đơn tại một phiên bản, kèm người thao tác và thời điểm để tra soát.
 */
@Entity @Table(name="attendance_correction_history") @Getter @Setter
public class AttendanceCorrectionHistory extends AuditRecord {
    @Column(nullable=false) private UUID requestId;
    @Column(nullable=false) private long requestVersion;
    @Column(nullable=false, length=30) private String action;
    /** Bản chụp dữ liệu phản hồi của đơn ở phiên bản tương ứng, dạng JSON. */
    @Column(nullable=false, columnDefinition="text") private String snapshot;
}
