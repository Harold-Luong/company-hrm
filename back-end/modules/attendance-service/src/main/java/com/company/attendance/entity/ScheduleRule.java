package com.company.attendance.entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Quy tắc phân ca lặp theo một ngày trong tuần, trong khoảng ngày có hiệu lực.
 * Có thể là lịch mặc định công ty hoặc lịch riêng của nhân viên; lịch riêng còn hiệu lực được ưu tiên.
 * Tham chiếu phiên bản ca bất biến để việc sửa mẫu ca không tự thay đổi quy tắc đã phân công.
 */
@Entity
@Table(name = "work_schedule_rules")
@Getter @Setter
public class ScheduleRule {
    @Id private UUID id;
    /** null là lịch mặc định công ty; có UUID là lịch riêng của nhân viên. */
    private UUID employeeId;
    @Column(nullable = false) private UUID batchId;
    /** Tham chiếu phiên bản ca bất biến; không sao chép JSON ca vào từng bản ghi. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({
        @JoinColumn(name = "shift_id", referencedColumnName = "shift_id", nullable = false),
        @JoinColumn(name = "shift_version", referencedColumnName = "shift_version", nullable = false)
    })
    @JsonIgnore
    private ShiftRevision shiftRevision;
    /** Ngày trong tuần theo ISO: 1 là thứ Hai, 7 là Chủ nhật. */
    @Column(nullable = false) private int weekday;
    /** Ngày đầu tiên có hiệu lực, được tính trong khoảng áp dụng. */
    @Column(nullable = false) private LocalDate effectiveFrom;
    /** Ngày cuối cùng có hiệu lực; 9999-12-31 biểu diễn lịch không đặt ngày kết thúc. */
    @Column(nullable = false) private LocalDate effectiveUntil;
    public UUID getShiftId() { return shiftRevision.getShiftId(); }
    public long getShiftVersion() { return shiftRevision.getShiftVersion(); }
    public String getDefinition() { return shiftRevision.getDefinition(); }
}
