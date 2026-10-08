# Đi trễ / về sớm — API đã kết nối

Route `/attendance/requests` dùng Attendance API thật, không còn ca mẫu, đơn mẫu
hoặc memory store. Lịch tham chiếu lấy `GET /api/v1/attendance/schedules/mine`
theo ngày chọn. HR/ADMIN mở **Hàng chờ duyệt** ngay trên trang; nhân viên xem,
sửa/rút đơn PENDING và lịch sử của mình. Danh sách phân trang/lọc trạng thái.

Prefix `/api/v1/attendance/requests`:

| Method / path | Chức năng |
| --- | --- |
| GET / | Danh sách bản thân, `status`, `page`, `size` |
| GET /inbox | Danh sách HR/ADMIN, cùng bộ lọc |
| POST / | Tạo đơn, `Idempotency-Key` bắt buộc |
| GET /{id} | Chi tiết và ETag, chủ đơn hoặc HR/ADMIN |
| PUT /{id} | Chủ đơn sửa PENDING, `If-Match` |
| POST /{id}/cancel | Chủ đơn rút PENDING, `If-Match` |
| POST /{id}/decision | HR/ADMIN duyệt/từ chối, `If-Match`, không tự duyệt |
| GET /{id}/history | Lịch sử có phân trang, chủ đơn hoặc HR/ADMIN |

Body gửi/sửa: `workDate`, `shiftId`, `shiftVersion`, `requestType`
(`LATE_ARRIVAL`/`EARLY_DEPARTURE`), `period` (`MORNING`/`AFTERNOON`),
`expectedTime` (`HH:mm`), `reason` (1–1.000 ký tự sau trim).
UI chỉ dùng `requestedMinutes` để xem trước; không gửi thời lượng, nhân viên hoặc
trạng thái tự quyết định. Server tính lại theo snapshot ca thật, trả `version`,
`requestedMinutes`, `roundedRequestedMinutes`, `definition` và thông tin xét duyệt.
Decision: `{"status":"APPROVED|REJECTED","reviewNote":"..."}`; từ chối bắt buộc lý do.

Ngày gửi/sửa từ hôm nay đến 365 ngày tới, giờ phải nằm trong buổi làm việc; nghỉ
cả buổi dùng Leave. Không nhận đơn trùng ngày/buổi/loại còn PENDING hoặc APPROVED,
không cho hai đơn trễ/sớm bao phủ hết buổi. Chặn ngày nghỉ chung hoặc buổi đã có
Leave PENDING/APPROVED. Ngày đã qua không sửa nhưng HR vẫn được xét duyệt đơn đã gửi.
Khi lịch đổi trong lúc chờ duyệt, yêu cầu tải lại/sửa trước khi duyệt. Phân công
không được thay lịch ảnh hưởng đơn đã duyệt. Đơn đã xử lý không sửa/rút trong bản này.

**Quy tắc đã thống nhất:** đơn được duyệt ghi nhận có phép, vẫn trừ công theo
bước làm tròn 15 phút. Không cộng thời gian làm, không trừ số dư phép năm.
Bảng công/CSV bổ sung `permissionCoverage`: ID đơn chờ/duyệt/xung đột, phút đã được
phép, giây thiếu thực tế nằm trong phạm vi được phép, số phút thiếu chưa có phép
làm tròn riêng để đối soát. Các trường có phép/chưa có phép không dùng để cộng lại
thay công chuẩn. Lý do/ghi chú riêng tư chỉ ở API đơn, không xuất CSV.

Ví dụ ca 13–15h, đơn xin tới 13:08 được duyệt, thực tế tới 13:16:
16 phút trễ → trừ 30 phút → 90 phút công; có phép 8 phút, còn 8 phút chưa có phép
→ đối soát 15 phút chưa có phép. Phê duyệt trong cùng Attendance DB hiển thị ở lần
đọc bảng công tiếp theo; snapshot Leave/Calendar vẫn theo quy tắc refresh coverage.

Khởi tạo database bằng [001_attendance_schema.sql](../../../back-end/modules/attendance-service/docs/sql/001_attendance_schema.sql)
trước khi chạy backend. File này đã gồm schema đơn đi trễ/về sớm; không cần migration riêng.
UI giữ key khi retry gửi đơn mất phản hồi; 412 yêu cầu tải lại dữ liệu và sửa lại.

Attendance hiện ghi một cặp vào/ra mỗi ngày. Xin theo buổi không tự tạo sự kiện
rời/quay lại trong ngày; chỉ thời gian thiếu đo được từ punch mới được đối soát.

