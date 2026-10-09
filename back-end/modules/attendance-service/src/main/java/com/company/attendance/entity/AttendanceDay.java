package com.company.attendance.entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import java.time.*;
import java.util.UUID;

/**
 * Lưu lần chấm công của một nhân viên trong một ngày làm việc; mỗi cặp nhân viên/ngày chỉ có một bản ghi.
 * Giữ giờ vào/ra, phiên bản ca và bản chụp dữ liệu nhân viên, phép, ngày nghỉ để tính lại công từ dữ liệu đã ghi nhận.
 * Kết quả công được tính khi đọc báo cáo, không lưu trực tiếp trong entity này.
 */
@Entity
@Table(name = "attendance_daily", uniqueConstraints = @UniqueConstraint(columnNames = {"employee_id", "work_date"}))
@Getter @Setter
public class AttendanceDay extends VersionedEntity {
    @Column(nullable = false) private UUID employeeId;
    /** Ngày bắt đầu ca; với ca qua đêm, giờ ra có thể thuộc ngày hôm sau. */
    @Column(nullable = false) private LocalDate workDate;
    /** Tham chiếu phiên bản ca bất biến; không sao chép JSON ca vào từng bản ghi. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({
        @JoinColumn(name = "shift_id", referencedColumnName = "shift_id", nullable = false),
        @JoinColumn(name = "shift_version", referencedColumnName = "shift_version", nullable = false)
    })
    @JsonIgnore
    private ShiftRevision shiftRevision;
    @Column(nullable = false, columnDefinition = "text") private String employeeSnapshot;
    /** Bản chụp dữ liệu phép; chỉ cập nhật lại qua thao tác làm mới dữ liệu nguồn có kiểm soát. */
    @Column(nullable = false, columnDefinition = "text") private String leaveSnapshot;
    @Column(nullable = false, columnDefinition = "text") private String holidaySnapshot;
    /** Thời điểm lấy dữ liệu nguồn gần nhất cho bản ghi ngày công. */
    @Column(nullable = false) private Instant sourceObservedAt;
    private Instant checkIn;
    private Instant checkOut;
    /** Approved effective times; original checkIn/checkOut and events remain untouched. */
    private Instant correctedCheckIn;
    private Instant correctedCheckOut;
    private UUID correctionId;
    public Instant getEffectiveCheckIn() { return correctionId == null ? checkIn : correctedCheckIn; }
    public Instant getEffectiveCheckOut() { return correctionId == null ? checkOut : correctedCheckOut; }
    public UUID getShiftId() { return shiftRevision.getShiftId(); }
    public long getShiftVersion() { return shiftRevision.getShiftVersion(); }
    public String getShiftDefinition() { return shiftRevision.getDefinition(); }
}
