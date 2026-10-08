package com.company.attendance.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Lưu yêu cầu và kết quả xử lý để chống thực hiện lặp khi client gửi lại cùng Idempotency-Key.
 * Khóa được xét theo người thao tác và loại thao tác; cùng khóa, cùng nội dung sẽ trả lại kết quả đã lưu.
 * Bản ghi này phục vụ phát lại kết quả, không thay thế lịch sử phiên bản của ca hoặc đơn.
 */
@Entity
@Table(name = "attendance_operations", uniqueConstraints = @UniqueConstraint(columnNames = {"actor_user_id", "operation_type", "request_key"}))
@Getter @Setter
public class AttendanceOperation extends AuditRecord {
    @Column(nullable = false, length = 50) private String operationType;
    @Column(nullable = false, length = 100) private String requestKey;
    /** Nội dung yêu cầu dạng JSON, dùng phát hiện cùng khóa nhưng gửi nội dung khác. */
    @Column(nullable = false, columnDefinition = "text") private String requestBody;
    /** Kết quả JSON để phát lại; null khi payload chấm công hết hạn, khóa chống trùng vẫn được giữ. */
    @Column(columnDefinition = "text") private String responseBody;
}
