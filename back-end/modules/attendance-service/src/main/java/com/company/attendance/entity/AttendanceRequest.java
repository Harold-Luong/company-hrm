package com.company.attendance.entity;
import com.company.attendance.enums.*;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import java.time.*;
import java.util.UUID;
/**
 * Đơn xin đi trễ hoặc về sớm của nhân viên, gắn với ngày làm việc, buổi và phiên bản ca cụ thể.
 * HR/ADMIN xét duyệt để xác định phần đi trễ/về sớm có phép; đơn được duyệt không tự cộng bù công hoặc trừ quỹ phép.
 * Thông tin xét duyệt và version được kế thừa từ ReviewableEntity.
 */
@Entity @Table(name="attendance_requests") @Getter @Setter
public class AttendanceRequest extends ReviewableEntity {
    @Column(nullable=false) private UUID employeeId;
    /** Mã tài khoản gửi đơn, dùng cùng employeeId để kiểm tra không tự duyệt. */
    @Column(nullable=false, length=255) private String requesterUserId;
    @Column(nullable=false, length=255) private String employeeCode;
    @Column(nullable=false, length=255) private String employeeName;
    @Column(nullable=false) private LocalDate workDate;
    /** Tham chiếu phiên bản ca bất biến; không sao chép JSON ca vào từng bản ghi. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({
        @JoinColumn(name = "shift_id", referencedColumnName = "shift_id", nullable = false),
        @JoinColumn(name = "shift_version", referencedColumnName = "shift_version", nullable = false)
    })
    @JsonIgnore
    private ShiftRevision shiftRevision;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=30) private AttendanceRequestType requestType;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private WorkPeriod period;
    /** Giờ dự kiến đến khi xin đi trễ, hoặc giờ dự kiến rời khi xin về sớm. */
    @Column(nullable=false) private LocalTime expectedTime;
    /** Số phút xin phép do backend tính từ giờ dự kiến và khoảng ca tương ứng. */
    @Column(nullable=false) private int requestedMinutes;
    @Column(nullable=false, length=1000) private String reason;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private AttendanceRequestStatus status;
    /** ACTIVE khi PENDING/APPROVED để khóa đơn trùng; null khi REJECTED/CANCELLED để cho phép gửi lại. */
    @Column(length=10) private String activeSlot;
    @Column(nullable=false) private Instant updatedAt;
    public UUID getShiftId() { return shiftRevision.getShiftId(); }
    public long getShiftVersion() { return shiftRevision.getShiftVersion(); }
    public String getShiftDefinition() { return shiftRevision.getDefinition(); }
}
