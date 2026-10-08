# Attendance Service

Spring Boot **4.1.1**, **Java 21**, **JPA/Hibernate**, PostgreSQL riêng `attendance_db`, cổng `8085`.
Cấu trúc controller → service → repository/entity giống Employee.
[Thiết kế tổng thể](../ATTENDANCE-DESIGN.md) mô tả cả phần mở rộng chưa triển khai.

## Phạm vi đã triển khai

- HR/ADMIN quản lý mẫu ca `FIXED_SHIFT`, phiên bản và lịch sử; vô hiệu hóa thay vì xóa lịch sử.
- Phân công có ngày hiệu lực, ngày trong tuần, xem trước tác động và lịch sử. `COMPANY_DEFAULT` giữ lịch riêng; `SELECTED_EMPLOYEES` áp lịch riêng; `ALL_EMPLOYEES` thay cả lịch riêng trong phạm vi ngày/weekday đã chọn.
- Đơn đi trễ/về sớm: gửi/sửa/rút PENDING, HR/ADMIN xét duyệt, lịch sử; kết nối UI `/attendance/requests`. Đơn duyệt ghi nhận có phép nhưng vẫn trừ công.
- Nhân viên tự check-in/out bằng thời gian server, một lần vào/ra mỗi ngày qua mạng công ty. Employee phải ACTIVE/PROBATION và đã tới ngày vào làm.
- Bảng công cá nhân, báo cáo HR/ADMIN, lọc nhân viên/phòng ban/ngày, CSV UTF-8 BOM cùng quy tắc tính với API.
- Kết hợp phép đã duyệt qua Leave và ngày nghỉ chung đã công bố qua Calendar. Chỉ đọc REST với Bearer token của người gọi, không truy vấn chéo database.
- Tham chiếu phiên bản ca bất biến, snapshot nhân viên/phép/ngày nghỉ tối thiểu; ghi sự kiện, kết quả và idempotency trong cùng transaction. Phiên bản `If-Match` chống ghi đè; khóa DB bảo vệ phân công/check-in đồng thời.

Chưa gồm `FLEXIBLE_DURATION`, nhiều lần vào/ra trong một ca,
điều chỉnh giờ thủ công, khóa kỳ, payroll, tự đồng bộ Kafka hoặc thay đổi sổ số dư Leave.
Báo cáo luôn `reportState=DRAFT`; không phải bảng công đã chốt. Khóa ghi hiện dùng
một hàng điều phối chung; cần đánh giá tải trước khi mở rộng quy mô.

## Chính sách chế độ chấm công

- **Hiện tại ưu tiên `FIXED_SHIFT`**: ca có giờ bắt đầu/kết thúc xác định, chẳng
  hạn 08:00–17:00 hoặc 08:00–17:30, cấu hình riêng các khoảng nghỉ. Ca part-time
  cố định như 13:00–17:00 cũng dùng chế độ này.
- **`FLEXIBLE_DURATION` để giai đoạn sau**: tính theo thời lượng phải làm, thuộc
  nhóm tính năng nâng cao và mặc định ẩn. Sau khi triển khai đầy đủ, chỉ ADMIN
  được mở khóa ở cấp công ty; HR/nhân viên không tự mở khóa. Backend phải kiểm
  tra trạng thái mở khóa, không chỉ ẩn lựa chọn trên UI.
- Hiện UI chỉ tạo ca cố định; backend từ chối tạo/sửa ca linh hoạt. Enum giữ
  `FLEXIBLE_DURATION` để mô tả hướng mở rộng. Chưa có nút hoặc API mở khóa trong
  phiên bản hiện tại; không thay đổi chính sách tính công của các ca đã lưu.

## Kiến trúc code và dữ liệu

- `AuditRecord` là `@MappedSuperclass` dùng chung `id`, `actorUserId`, `occurredAt`
  và `recordAudit(actor, time)`. `ShiftRevision`, `ScheduleBatch`,
  `AttendanceRequestHistory`, `AttendanceEvent`, `AttendanceOperation` kế thừa.
  Thời gian lấy từ `Clock` của service, người thao tác lấy từ principal đã xác thực.
- `VersionedEntity` dùng chung UUID và optimistic lock `version`.
  `ReviewableEntity` mở rộng thêm thời điểm tạo, người duyệt, thời điểm duyệt và
  ghi chú cho đơn đi trễ/về sớm và OT. Quyền duyệt, trạng thái
  và kiểm tra xung đột vẫn thuộc service của từng nghiệp vụ.
- Kế thừa Java không tạo bảng cha hay JOIN giữa các bảng. Giữ bảng lịch sử riêng
  cùng FK/unique version để truy nguyên được đối tượng và phiên bản. Operations
  vẫn là kho idempotency với unique actor/type/key; events vẫn dùng cột `event_at`
  và trường JSON `eventAt`. Audit và thay đổi nghiệp vụ được ghi cùng transaction.
- `AttendanceDay`, `ScheduleRule`, `AttendanceRequest` tham chiếu trực tiếp
  `ShiftRevision` qua FK `(shift_id, shift_version)`, không lưu JSON ca lặp lại.
  Revision được đánh dấu bất biến trong JPA và có trigger PostgreSQL chặn UPDATE;
  FK ngăn xóa revision đang được dùng. Các truy vấn báo cáo tải ca cùng bản ghi
  bằng entity graph, không phát sinh thêm truy vấn cho mỗi ngày.
- `AttendanceDay` vẫn unique `(employee_id, work_date)`; `AttendanceEvent` unique
  `(day_id, event_type)` theo quy tắc một lần vào/ra mỗi ngày hiện tại. Snapshot
  nhân viên chỉ giữ ID, mã, họ tên, ID phòng ban; snapshot phép chỉ giữ đơn
  PENDING/APPROVED đúng nhân viên/ngày, ngày nghỉ chỉ giữ sự kiện thuộc ngày đó.
- Báo cáo đọc ngày công, lịch, đơn còn hiệu lực và OT theo khoảng ngày: **4 truy vấn
  DB mỗi nhân viên**, không truy vấn lại cho từng ngày. Lịch cá nhân dùng **2 truy vấn**
  cho cả khoảng ngày. Con số này không bao gồm REST đến Employee/Leave/Calendar.
  Snapshot ngày đã chấm công được ưu tiên; ngày chưa ghi nhận dùng lịch riêng còn
  hiệu lực rồi mới đến lịch mặc định. Không cache xuyên request.
- `AttendanceCalculator` tính công; `AttendancePermissionService` giải thích phần
  đi trễ/về sớm được duyệt; `OvertimeService` tính OT riêng. `AttendanceCsvExporter`
  xuất cùng dữ liệu báo cáo JSON, giữ BOM và escape công thức CSV.
- [WorkTimeSupport](docs/WORKTIME-CALCULATION.md) cung cấp các hàm nội bộ
  `calculate(input)` và `calculatePeriod(inputs)` để tính công hưởng lương từ
  tham số. Helper và kiểm thử nằm trong module Attendance, không có POM/dependency
  riêng. API/UI hiện hành chưa sử dụng các trường kết quả mở rộng này.

[Schema duy nhất](docs/sql/001_attendance_schema.sql) khai báo trực tiếp toàn bộ
bảng, index, FK và trigger hiện hành. Trong giai đoạn phát triển, cập nhật file
này và tạo lại database khi thay đổi cấu trúc; không duy trì chuỗi migration.

Idempotency mặc định giữ payload trả về của `CHECK_IN`, `CHECK_OUT`, `OT_CHECK_IN`,
`OT_CHECK_OUT` trong 180 ngày. Job chạy 03:00 giờ Việt Nam, tối đa 500 payload/lần;
chỉ đặt `response_body=null`, giữ khóa và yêu cầu gốc. Gửi lại khóa đã hết payload
trả HTTP 409, không thực hiện lại nghiệp vụ. `APPLY_SCHEDULE`, `DECIDE_OT`,
`REFRESH_COVERAGE` và các lịch sử nghiệp vụ không bị dọn. Cấu hình bằng
`ATTENDANCE_IDEMPOTENCY_RETENTION_DAYS` (0 để tắt) và
`ATTENDANCE_IDEMPOTENCY_CLEANUP_CRON` (`-` để tắt lịch chạy).

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

Khi UI chạy bằng Vite trên cùng máy, Gateway nhận kết nối từ Vite và chuyển IP
loopback (`127.0.0.1` hoặc `::1`) tới Attendance, không phải IP Internet của máy.
Vì vậy mặc định local có loopback trong cả `allowed-networks` và `trusted-proxies`.
`trusted-proxies` chỉ cho phép đọc IP từ header của Gateway, không cấp quyền chấm
công cho IP đó. Biến `ATTENDANCE_ALLOWED_NETWORKS` nếu được đặt sẽ thay toàn bộ
danh sách mặc định. Khi triển khai, cấu hình allowlist theo IP client mà Gateway
thực sự nhìn thấy; Vite dev proxy không dùng để xác minh mạng công ty trong production.

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
| `ATTENDANCE_ALLOWED_NETWORKS`                   | `127.0.0.1/32,::1/128,113.161.73.224/32,10.0.0.0/8` |
| `ATTENDANCE_TRUSTED_PROXIES`                    | `127.0.0.1/32,::1/128`; Gateway chạy cùng máy khi phát triển |

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

Ca mặc định seed `08:00–12:00`, `13:30–17:30`. Khi khởi động, nếu chưa có lịch
công ty, service tự lưu lịch thứ Hai–thứ Sáu từ ngày khởi tạo, không ngày kết thúc.
Nhân viên đủ điều kiện dùng lịch này ngay, không cần HR phân công từng người.
HR dùng **Phân công → Xem trước → Áp dụng** khi muốn thay đổi lịch.
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

## API đơn đi trễ/về sớm

Contract, ví dụ đối soát và UI: [Requests UI/API](../../../frontend/src/attendance/REQUESTS-UI.md).
Các route đều dưới `/api/v1/attendance/requests`, dùng JWT hiện tại. `GET /` của
người gọi; `GET /inbox` dành HR/ADMIN; `POST /` cần Idempotency-Key; `PUT /{id}`,
`POST /{id}/cancel`, `POST /{id}/decision` dùng If-Match. GET chi tiết/history chỉ
chủ đơn hoặc HR/ADMIN. Không tự duyệt, không sửa/rút đơn đã xử lý.

Yêu cầu ngày trong 365 ngày tới, giờ trong buổi của ca thực tế, reason 1–1.000 ký tự.
Snapshot ca/nhân viên và thời lượng server tính; lịch thay đổi phải cập nhật đơn chờ
trước khi duyệt. Ca không được thay đổi nếu ảnh hưởng đơn APPROVED. Kiểm tra trùng
và xét duyệt chạy dưới cùng khóa DB với phân ca/check-in, audit cùng transaction.
Trùng slot PENDING/APPROVED còn có unique constraint tại database.

Bảng công và CSV thêm permissionCoverage/nhóm cột request: không lộ reason/reviewNote.
Phút công vẫn là phần phải làm trừ trễ/sớm làm tròn 15 phút; đơn không tạo sự kiện
vào/ra, không cộng công và không thay số dư Leave. Phần được phép chỉ bao phủ phút
thiếu thực tế nằm trong khoảng xin phép; không bù phần vượt giờ đã xin.
Thiếu punch không suy ra giờ thực tế. Khi phép/ngày nghỉ mới xung đột với đơn duyệt,
sau refresh nguồn, ID đơn nằm trong conflictingRequestIds để HR đối soát.

Phê duyệt trong cùng database có hiệu lực ở lần đọc báo cáo tiếp theo; báo cáo vẫn
DRAFT. Leave/Calendar kiểm tra qua REST chưa có transaction xuyên service: nếu
nguồn thay đổi đồng thời hoặc sau khi duyệt, cần refresh/đối soát; không tự hủy
phép hoặc đơn đã duyệt. Mỗi đơn có nhật ký trước/sau theo các snapshot version.

Hiện Attendance chỉ ghi một cặp vào/ra mỗi ngày. Đơn có thể xin theo buổi, nhưng
phần thiếu thực tế chỉ đối soát từ cặp giờ đã ghi; chưa đo việc rời/quay lại giữa
hai buổi nếu không có sự kiện tương ứng. Không tự suy ra giờ vắng từ đơn được duyệt.


## Ca cố định, lịch mặc định và lịch riêng

Mỗi ca dùng `FIXED_SHIFT`, có giờ bắt đầu/kết thúc cụ thể cho từng khoảng làm việc,
ví dụ 08:00–12:00 và 13:30–17:30. HR chọn mẫu ca để áp dụng theo các thứ
và ngày hiệu lực; không tạo lịch luân phiên theo chu kỳ.

- Ca hành chính: service tự khởi tạo `COMPANY_DEFAULT`, thứ Hai–thứ Sáu, `until: null`,
  nếu chưa có lịch công ty. Quy tắc lặp theo tuần, nhân viên mới đủ điều kiện tự dùng mặc định.
  Không cần cron hoặc tạo bản ghi phân công cho từng nhân viên/ngày.
- Lịch riêng: `SELECTED_EMPLOYEES` có ngày hiệu lực, ưu tiên hơn mặc định.
  Khi lịch riêng hết hạn, lịch mặc định có hiệu lực trở lại.

Ca qua đêm dùng `overnight: true`, một khoảng giờ, ví dụ `22:00–06:00`,
`checkInFrom: 21:00`, `checkOutUntil: 07:00`. Ngày công/phép là ngày bắt đầu ca;
giờ kết thúc và cuối cửa sổ chấm công thuộc ngày sau. Tổng cửa sổ dưới 24 giờ.
Ca cũ mặc định `overnight: false`. Chặn ca đêm chồng giờ với ca kế tiếp.

## OT cần duyệt trước khi làm

UI `/attendance/overtime`, API `/api/v1/attendance/overtime`:

| API | Chức năng |
| --- | --- |
| `GET /overtime` | Danh sách đơn của mình; lọc `status`, `page`, `size` |
| `GET /overtime/inbox` | HR/Admin xem đơn cần xử lý |
| `POST /overtime` | Gửi đơn, bắt buộc `Idempotency-Key` |
| `POST /overtime/{id}/cancel` | Chủ đơn rút PENDING, dùng `If-Match` |
| `POST /overtime/{id}/decision` | HR/Admin duyệt/từ chối, dùng `If-Match`; không tự duyệt |
| `POST /overtime/{id}/check-in` | Chủ đơn bắt đầu OT đã duyệt qua mạng công ty, dùng `Idempotency-Key` |
| `POST /overtime/{id}/check-out` | Kết thúc OT qua mạng công ty, dùng `Idempotency-Key` |

Body đăng ký: `{"start":"2030-01-07T18:00","end":"2030-01-07T20:00","reason":"Bảo trì"}`.
Thời gian đăng ký theo `Asia/Ho_Chi_Minh`, chính xác đến phút, dưới 24 giờ,
trong 365 ngày tới. Đăng ký và duyệt phải trước giờ bắt đầu. Có thể qua đêm
hoặc ngày nghỉ. OT không trùng giờ làm thường, phép hay đơn OT PENDING/APPROVED.
Backend kiểm tra lại xung đột nguồn khi bắt đầu OT. Đơn bị từ chối cần lý do.

OT chấm giờ riêng với ca thường. Chỉ đơn APPROVED được ghi nhận; chưa đủ hai mốc
thì `countedMinutes: null`. Khi kết thúc, số phút là phần giao giữa khoảng thực tế
và khoảng được duyệt, lấy phút tròn xuống; kết thúc muộn không làm tăng OT quá mức
đã duyệt. Không chuyển giờ ở lại sau ca thường thành OT và không bù trừ trễ/sớm.
OT qua đêm thuộc ngày bắt đầu OT. Chưa tính hệ số tiền lương hoặc chốt payroll.

Bảng công có `overtime.approvedMinutes`, `overtime.countedMinutes`, `pendingIds`,
`approvedIds`. Tổng `countedMinutes` chỉ cộng đơn hoàn tất. CSV thêm
`approved_ot_minutes`, `counted_ot_minutes`; OT không nhập vào `workMinutesCounted`.
Preview phân công thêm `approvedOvertimeConflicts`; chặn sửa lịch ảnh hưởng OT đã duyệt.
Ngày giờ, người duyệt, IP chấm giờ và kết quả thao tác được lưu trong DB.


### Khởi tạo lịch mặc định tự động

`DefaultScheduleInitializer` chạy lúc service khởi động, dùng mẫu ca seed UUID
`00000000-0000-0000-0000-000000000001`. Lưu 5 quy tắc chung trong
`work_schedule_rules`, batch lịch sử với actor `SYSTEM_DEFAULT_SCHEDULE` và tăng
`scheduleRevision` trong cùng transaction, có khóa DB để tránh tạo trùng khi nhiều instance khởi động.

Lịch có hiệu lực từ ngày service khởi tạo theo giờ Việt Nam; không bổ sung công
cho những ngày quá khứ. Thứ Bảy/Chủ nhật không có ca mặc định. Lịch riêng vẫn ưu tiên.
Nếu HR đã cấu hình bất kỳ lịch công ty nào (kể cả lịch có hiệu lực tương lai hoặc đã hết hạn),
initializer giữ nguyên cấu hình đó, không tự lấp ngày còn trống. Mẫu ca bị vô hiệu hóa
hoặc bị xóa cũng không được tự khôi phục. Các trường hợp này cần HR điều chỉnh lịch.

Sau khi tạo database từ schema hiện tại, khởi động backend để tạo lịch mặc định
còn thiếu. Khởi động lại nhiều lần không tạo thêm
bản ghi hoặc thay phiên bản khi lịch mặc định đã tồn tại. Test fixture có thể tắt bước
khởi tạo bằng `attendance.initialize-default-schedule=false`.
