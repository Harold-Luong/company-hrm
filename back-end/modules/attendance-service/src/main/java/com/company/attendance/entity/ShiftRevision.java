package com.company.attendance.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

/**
 * Lưu bản chụp từng phiên bản mẫu ca sau khi tạo, sửa hoặc vô hiệu hóa.
 * Mỗi cặp shiftId/shiftVersion là duy nhất, giúp tra lại định nghĩa ca và người thay đổi tại thời điểm đó.
 */
@org.hibernate.annotations.Immutable
@Entity
@Table(name = "shift_revisions", uniqueConstraints = @UniqueConstraint(columnNames = {"shift_id", "shift_version"}))
@Getter @Setter
public class ShiftRevision extends AuditRecord {
    @Column(name = "shift_id", nullable = false) private UUID shiftId;
    @Column(name = "shift_version", nullable = false) private long shiftVersion;
    @Column(nullable = false, columnDefinition = "text") private String definition;
    @Column(nullable = false) private boolean active;
}
