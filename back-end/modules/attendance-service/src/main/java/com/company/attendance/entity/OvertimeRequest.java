package com.company.attendance.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.*;
import java.util.UUID;
/**
 * Đơn đăng ký tăng ca với khoảng giờ dự kiến, trạng thái xét duyệt và giờ vào/ra OT riêng.
 * Chỉ OT đã duyệt mới được ghi nhận; phút OT tính theo phần giao giữa thời gian thực tế và khoảng được duyệt.
 * Giờ OT được tách khỏi chấm công ca thường, không tự phát sinh từ việc ở lại muộn.
 */
@Entity @Table(name="attendance_overtime_requests") @Getter @Setter
public class OvertimeRequest extends ReviewableEntity {
    @Column(nullable=false) private UUID employeeId;
    @Column(nullable=false) private String requesterUserId;
    @Column(nullable=false) private String employeeName;
    /** Ngày bắt đầu khoảng OT, kể cả khi OT kết thúc vào ngày hôm sau. */
    @Column(nullable=false) private LocalDate workDate;
    /** Giờ bắt đầu OT theo múi giờ nghiệp vụ Việt Nam; endTime là giờ kết thúc dự kiến. */
    @Column(nullable=false) private LocalDateTime startTime;
    @Column(nullable=false) private LocalDateTime endTime;
    @Column(nullable=false,length=1000) private String reason;
    @Column(nullable=false,length=20) private String status;
    /** Giờ vào OT thực tế dạng Instant; không dùng giờ vào của ca thường. */
    private Instant checkIn;
    private Instant checkOut;
    @Column(length=100) private String checkInIp;
    @Column(length=100) private String checkOutIp;
}
