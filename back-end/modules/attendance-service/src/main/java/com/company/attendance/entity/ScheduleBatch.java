package com.company.attendance.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Lưu lịch sử một đợt áp dụng phân công, gồm yêu cầu và các quy tắc lịch bị thay thế.
 * Các ScheduleRule sinh trong đợt phân công tham chiếu đến bản ghi này qua batchId.
 * Người thao tác và thời điểm được kế thừa từ AuditRecord.
 */
@Entity
@Table(name = "attendance_schedule_batches")
@Getter @Setter
public class ScheduleBatch extends AuditRecord {
    @Column(nullable = false) private long scheduleRevision;
    @Column(nullable = false, columnDefinition = "text") private String requestBody;
    /** Bản chụp JSON các quy tắc bị tác động trước khi tách khoảng hiệu lực hoặc thay thế. */
    @Column(nullable = false, columnDefinition = "text") private String replacedRules;
}
