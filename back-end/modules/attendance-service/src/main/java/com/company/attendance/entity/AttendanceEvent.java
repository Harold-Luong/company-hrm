package com.company.attendance.entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

/**
 * Lưu lịch sử từng thao tác ghi nhận giờ vào hoặc giờ ra của một ngày công.
 * Kế thừa người thực hiện và thời điểm từ AuditRecord; bổ sung phương thức chấm công và IP nguồn để tra soát.
 */
@Entity
@Table(name = "attendance_events", uniqueConstraints = @UniqueConstraint(columnNames = {"day_id", "event_type"}))
@AttributeOverride(name = "occurredAt", column = @Column(name = "event_at", nullable = false, updatable = false))
@Getter @Setter
public class AttendanceEvent extends AuditRecord {
    @Column(nullable = false) private UUID dayId;
    @Column(nullable = false, length = 30) private String eventType;
    @Column(nullable = false, length = 30) private String method;
    @Column(nullable = false, length = 100) private String sourceIp;

    // Giữ tên eventAt trên API; trong entity dùng chung occurredAt và ánh xạ vào cột event_at.
    @Override
    @JsonIgnore
    public Instant getOccurredAt() { return super.getOccurredAt(); }

    public Instant getEventAt() { return super.getOccurredAt(); }
    public void setEventAt(Instant time) { setOccurredAt(time); }
}
