package com.company.attendance.enums;

/** Chế độ tính công theo chính sách công ty, độc lập với hợp đồng full-time/part-time. */
public enum AttendanceMode {
    /** Chế độ mặc định hiện tại: làm theo khung giờ ca, kể cả ca part-time cố định. */
    FIXED_SHIFT,

    /**
     * Dự kiến cho giai đoạn sau: tính theo tổng thời lượng, không bắt buộc giờ vào/ra cố định.
     * Tính năng nâng cao, mặc định ẩn; chỉ ADMIN được mở khóa sau khi triển khai đầy đủ.
     * Hiện chưa hỗ trợ và ShiftService từ chối tạo/sửa ca thuộc chế độ này.
     */
    FLEXIBLE_DURATION
}
