# Attendance Service

Spring Boot **4.1.1**, **Java 21**, **JPA/Hibernate**, PostgreSQL riêng `attendance_db`, cổng `8085`.
Cấu trúc controller → service → repository/entity giống Employee.
[Thiết kế tổng thể](../ATTENDANCE-DESIGN.md) mô tả cả phần mở rộng chưa triển khai.

## Phạm vi đã triển khai

- HR/ADMIN quản lý mẫu ca `FIXED_SHIFT`, phiên bản và lịch sử; vô hiệu hóa thay vì xóa lịch sử.
- Phân công có ngày hiệu lực, ngày trong tuần, xem trước tác động và lịch sử. `COMPANY_DEFAULT` giữ lịch riêng; `SELECTED_EMPLOYEES` áp lịch riêng; `ALL_EMPLOYEES` thay cả lịch riêng trong phạm vi ngày/weekday đã chọn.
- Nhân viên tự check-in/out bằng thời gian server, một lần vào/ra mỗi ngày qua mạng công ty. Employee phải ACTIVE/PROBATION và đã tới ngày vào làm.
- Bảng công cá nhân, báo cáo HR/ADMIN, lọc nhân viên/phòng ban/ngày, CSV UTF-8 BOM cùng quy tắc tính với API.
- Kết hợp phép đã duyệt qua Leave và ngày nghỉ chung đã công bố qua Calendar. Chỉ đọc REST với Bearer token của người gọi, không truy vấn chéo database.
- Snapshot ca, Employee, phép/ngày nghỉ; ghi sự kiện, kết quả và idempotency trong cùng transaction. Phiên bản `If-Match` chống ghi đè; khóa DB bảo vệ phân công/check-in đồng thời.

Chưa gồm `FLEXIBLE_DURATION`, đơn xin đi trễ/về sớm, ca qua đêm, nhiều lần vào/ra,
điều chỉnh giờ thủ công, khóa kỳ, payroll, tự đồng bộ Kafka hoặc thay đổi sổ số dư Leave.
Báo cáo luôn `reportState=DRAFT`; không phải bảng công đã chốt. Khóa ghi hiện dùng
một hàng điều phối chung; cần đánh giá tải trước khi mở rộng quy mô.

## Chạy local

Yêu cầu JDK 21, PostgreSQL, public key Auth và các service Employee/Leave/Calendar.
Tạo user/database riêng rồi áp [schema SQL](docs/sql/README.md) trước khi chạy.
JPA dùng `ddl-auto=validate`, không tự tạo bảng trong ứng dụng.

```bash
# Chạy từ back-end/modules/attendance-service; psql hỏi mật khẩu.
psql -h localhost -U attendance_user -d attendance_db -v ON_ERROR_STOP=1 -f docs/sql/001_attendance_schema.sql
# Chỉ dành cho thử nghiệm từ cùng máy qua Gateway local:
export ATTENDANCE_ALLOWED_NETWORKS='127.0.0.1/32,::1/128'
export ATTENDANCE_TRUSTED_PROXIES='127.0.0.1/32,::1/128'
./mvnw spring-boot:run
```

Spring Boot không tự đọc `.env`. Export biến trong shell/IDE, hoặc dùng `./run-all.sh`
tại gốc sau khi chuẩn bị database cho các service. Trong môi trường thật, cấu hình
allowlist là IP/CIDR mạng công ty mà Gateway nhìn thấy; trusted proxies chỉ là địa chỉ
Gateway đáng tin cậy. Để trống allowlist sẽ từ chối check-in/out. Không tin header
`X-Forwarded-For` từ peer bên ngoài danh sách proxy. Gateway hiện ghi lại header từ
TCP peer; nếu có reverse proxy bên ngoài Gateway cần thiết kế chuỗi tin cậy trước.

| Biến                                            | Mặc định local                                        |
| ----------------------------------------------- | ----------------------------------------------------- |
| `ATTENDANCE_PORT`                               | `8085`                                                |
| `ATTENDANCE_DB_URL`                             | `jdbc:postgresql://localhost:5432/attendance_db`      |
| `ATTENDANCE_DB_USER` / `ATTENDANCE_DB_PASSWORD` | `attendance_user` / `attendance_password` (chỉ local) |
| `JWT_ACCESS_PUBLIC_KEY`                         | `file:../auth-service-main/keys/access-public.pem`    |
| `JWT_ACCESS_ISSUER` / `JWT_ACCESS_AUDIENCE`     | `auth-service` / `hrm-api-access`                     |
| `EMPLOYEE_SERVICE_URL`                          | `http://localhost:8082`                               |
| `LEAVE_SERVICE_URL`                             | `http://localhost:8084`                               |
| `CALENDAR_SERVICE_URL`                          | `http://localhost:8083`                               |
| `ATTENDANCE_ALLOWED_NETWORKS`                   | Rỗng; CIDR phân cách bằng dấu phẩy                    |
| `ATTENDANCE_TRUSTED_PROXIES`                    | Rỗng; CIDR phân cách bằng dấu phẩy                    |

Compose tại `../infra/gateway` có database, mount schema và URL các nguồn nội bộ.
Gateway giữ nguyên `/api/v1/attendance/**`. CORS do Gateway xử lý.
Swagger trực tiếp: `http://localhost:8085/swagger-ui.html`; OpenAPI: `/v3/api-docs`.
JWT RS256 được xác minh cục bộ theo cùng contract Auth/Employee.

## API

Prefix `/api/v1/attendance`, mọi API nghiệp vụ cần Bearer JWT.

| Method / đường dẫn                                    | Quyền / chức năng                                     |
| ----------------------------------------------------- | ----------------------------------------------------- |
| `GET /health-check`                                   | Đã đăng nhập; JPA/database health                     |
| `GET, POST /shifts`                                   | HR/ADMIN; danh sách, tạo ca                           |
| `GET, PUT, DELETE /shifts/{id}`                       | HR/ADMIN; xem, sửa có version, vô hiệu hóa            |
| `GET /shifts/{id}/history`                            | HR/ADMIN; phiên bản mẫu ca                            |
| `POST /schedules/preview`                             | HR/ADMIN; tác động và `scheduleRevision`              |
| `POST /schedules/apply`                               | HR/ADMIN; áp phân công đã xem trước                   |
| `GET /schedules/history`                              | HR/ADMIN; lịch sử phân công                           |
| `GET /schedules/mine?from=&until=`                    | Lịch bản thân                                         |
| `GET /schedules/employees/{id}?from=&until=`          | HR/ADMIN; lịch nhân viên                              |
| `POST /check-in`, `POST /check-out`                   | Bản thân, không gửi thời gian/employeeId              |
| `GET /mine?from=&until=`                              | Công bản thân                                         |
| `GET /reports?from=&until=&employeeId=&departmentId=` | HR/ADMIN; báo cáo                                     |
| `GET /reports/export.csv`                             | HR/ADMIN; cùng bộ lọc báo cáo                         |
| `POST /employees/{id}/days/{date}/refresh-coverage`   | HR/ADMIN; lấy lại phép/ngày nghỉ cho ngày đã ghi công |
| `GET /employees/{id}/days/{date}/events`              | Bản thân hoặc HR/ADMIN; sự kiện gốc                   |

Danh sách ca/lịch sử dùng `page=0&size=20` (size tối đa 100).
Báo cáo tối đa 366 ngày, 5.000 dòng, không nhận ngày tương lai; lọc phòng ban theo
hồ sơ Employee hiện tại. Lịch cá nhân có thể xem tương lai trong phạm vi 366 ngày.
Nguồn lỗi trả lỗi, không xem như không có phép/ngày nghỉ. API Leave phục vụ đối soát
`GET /api/v1/leave/requests/attendance` chỉ trả trường tính công, không có lý do riêng tư.

`PUT/DELETE /shifts/{id}` gửi `If-Match: "<version>"`.
`POST /schedules/apply` gửi `If-Match: "<scheduleRevision>"` từ preview và
`Idempotency-Key`. Check-in/out cũng bắt buộc `Idempotency-Key` (1–100 ký tự).
Cùng người gọi, thao tác và key sẽ trả kết quả cũ; đổi payload với key cũ trả 409.
Refresh coverage gửi `If-Match: "<recordVersion>"`.
Thiếu version trả 428; version cũ 412; xung đột 409; quyền/mạng không hợp lệ 403;
nguồn chưa sẵn sàng 503. Hãy tải lại khi 412 và giữ nguyên key khi thử lại thao tác
chưa biết đã thành công hay chưa.

Ví dụ mẫu ca ngắn (POST `/shifts`):

```json
{
    "name": "Ca chiều 2 giờ",
    "mode": "FIXED_SHIFT",
    "timezone": "Asia/Ho_Chi_Minh",
    "intervals": [{ "period": "AFTERNOON", "start": "13:00", "end": "15:00" }],
    "checkInFrom": "12:00",
    "checkOutUntil": "16:00"
}
```

Ví dụ preview rồi apply cùng payload (thay UUID và ngày thực tế):

```json
{
    "shiftId": "00000000-0000-0000-0000-000000000001",
    "shiftVersion": 0,
    "scope": "COMPANY_DEFAULT",
    "employeeIds": [],
    "from": "2030-01-07",
    "until": null,
    "weekdays": ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"],
    "reason": "Áp dụng lịch làm việc công ty"
}
```

Thay đổi phải có hiệu lực từ hôm nay trở đi. `until=null` nghĩa là chưa có ngày
kết thúc. Phân công chụp phiên bản ca: sửa mẫu không tự sửa lịch đã phân. Muốn áp
mẫu mới phải preview/apply lại. Ngày đã ghi công hoặc phép đã duyệt bị tác động
sẽ chặn apply; lịch cũ được tách theo khoảng hiệu lực và lưu audit.

## Quy tắc tính và UI

Ca mặc định seed `08:00–12:00`, `13:30–17:30`, chưa tự gán nhân viên hoặc ngày
trong tuần. HR chọn **Phân công → Xem trước → Áp dụng** trước khi nhân viên chấm công.
Hỗ trợ 1–2 khoảng cùng ngày, không giao nhau, gắn MORNING/AFTERNOON để ánh xạ phép.
Nghỉ trưa/giờ ngoài ca không tính công và không bù cho đi trễ trong ca cố định.

Phép chỉ APPROVED mới giảm phần ca phải làm; PENDING hiển thị riêng. Phép nửa ngày
khớp buổi, FULL_DAY khớp cả ca. Phép chồng nhau, không khớp buổi hoặc trùng thời
gian làm thực tế trả SOURCE_CONFLICT và không kết luận số phút công.
Ngày nghỉ PUBLISHED/HOLIDAY/ALL/allDay không yêu cầu làm việc.
Đi trễ và về sớm mỗi loại làm tròn lên 15 phút; thiếu giờ vào/ra trả null thay vì
suy ra thời gian làm. Thời gian thực tế giữ giây, số phút công không âm.
Ví dụ nghỉ sáng + 13:38–17:30: phép 0,5 ngày/240 phút, thực tế 232 phút, công 225 phút.

Ngày đã check-in giữ snapshot nguồn tại thời điểm ghi nhận. Khi phép hoặc ngày
nghỉ thay đổi, HR dùng **Cập nhật phép/lịch** để đối soát; thao tác có version và
audit, không đổi ca/giờ vào ra. Chưa có transaction xuyên các service hoặc tự đồng bộ.

Frontend đã kết nối API thật, dùng phiên đăng nhập sẵn có:

| Trang                                          | Đường dẫn               |
| ---------------------------------------------- | ----------------------- |
| Công của tôi: giờ vào/ra, ca hôm nay, lịch sử  | `/attendance`           |
| Ca làm việc: tạo/sửa/vô hiệu hóa/lịch sử       | `/attendance/shifts`    |
| Phân công: phạm vi, hiệu lực, preview, lịch sử | `/attendance/schedules` |
| Bảng công: bộ lọc, đối soát, tải CSV           | `/attendance/reports`   |

Ba trang quản lý chỉ dành HR/ADMIN; API vẫn tự kiểm tra quyền.

## Kiểm thử

```bash
./mvnw test
./mvnw package
```

Mặc định dùng H2 theo schema SQL và RSA key tạm. Kiểm tra JWT/health, phân quyền,
version, idempotency, snapshot, làm tròn, CSV, giao dịch đồng thời và nguồn lỗi.
API integration có thể chạy với PostgreSQL **database test riêng** qua
`ATTENDANCE_TEST_DB_URL`, `ATTENDANCE_TEST_DB_USER`, `ATTENDANCE_TEST_DB_PASSWORD`.
Test này xóa dữ liệu các bảng Attendance trước từng ca; không trỏ vào DB ứng dụng.
Các nguồn Employee/Leave/Calendar được thay bằng fixture trong test API;
Leave có test PostgreSQL riêng cho endpoint coverage. Browser tests sử dụng API mock,
không thay thế kiểm tra triển khai đầy đủ nhiều service.
