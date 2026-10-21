# Attendance Service

Chạy bằng container riêng: dùng `Dockerfile`, `compose.yaml` và
`.env.docker.example` tại thư mục này. Xem [hướng dẫn Docker độc lập](../../../README.md#docker-independent)
để chuẩn bị network, cấu hình kết nối, build/start/stop riêng hoặc phát hành image.

**Mục lục**

- [Cấu hình và chạy độc lập](#cấu-hình-và-chạy-độc-lập)
- [Phạm vi đã triển khai](#phạm-vi-đã-triển-khai)
- [Chính sách chế độ chấm công](#chính-sách-chế-độ-chấm-công)
- [Kiến trúc code và dữ liệu](#kiến-trúc-code-và-dữ-liệu)
- [Chạy local](#chạy-local)
- [API](#api)
- [Quy tắc tính và UI](#quy-tắc-tính-và-ui)
- [Kiểm thử](#kiểm-thử)
- [API đơn đi trễ/về sớm](#api-đơn-đi-trễvề-sớm)
- [Ca cố định, lịch mặc định và lịch riêng](#ca-cố-định-lịch-mặc-định-và-lịch-riêng)
- [OT cần duyệt trước khi làm](#ot-cần-duyệt-trước-khi-làm)
- [Đơn bổ sung / điều chỉnh chấm công](#đơn-bổ-sung--điều-chỉnh-chấm-công)
- [Attendance database](#attendance-database)
- [Helper tính công trong Attendance](#helper-tính-công-trong-attendance)
- [Thiết kế chung: lịch làm việc, nghỉ phép và chấm công](#thiết-kế-chung-lịch-làm-việc-nghỉ-phép-và-chấm-công)

Spring Boot **4.1.1**, **Java 21**, **JPA/Hibernate**, PostgreSQL riêng `attendance_db`, cổng `8085`.
Cấu trúc controller → service → repository/entity giống Employee.
[Thiết kế tổng thể](#design) mô tả cả phần mở rộng chưa triển khai.

Cấu hình `.env` và cách chạy Maven/Git Bash: [hướng dẫn chạy độc lập](../../../README.md#local). Mẫu `.env` dùng PostgreSQL `55432` và allowlist loopback; bảng bên dưới ghi mặc định trong mã nguồn.

Thông báo đơn đi trễ/về sớm có outbox JPA; gửi Kafka khi cấu hình topic và `HRM_NOTIFICATIONS_ENABLED=true`. Bảng outbox nằm trong schema `001`; database cũ thiếu bảng cần chạy `004_notification_outbox.sql` dù đang tắt thông báo. Xem [Notification](../notification-service/README.md). Điều chỉnh công/OT chưa phát thông báo qua luồng này.

`GET /api/v1/attendance/requests/pending-count` trả `{ "count": N }` cho HR/ADMIN,
đếm cả đơn đi trễ và về sớm đang `PENDING`, phục vụ badge hàng chờ duyệt.

## Đồng bộ coverage và chốt kỳ công theo tháng

Áp [005_attendance_periods.sql](docs/sql/005_attendance_periods.sql) lên database cũ;
schema 001 đã gồm hai bảng mới `attendance_periods`, `attendance_period_history`.

Báo cáo kỳ mở tự lấy phép/ngày lễ, cập nhật coverage của ngày đã ghi công khi nội dung
nguồn đổi. Giữ ca, giờ gốc và correction; tăng recordVersion để đơn điều chỉnh cũ phải
đối chiếu lại. `refresh-coverage` vẫn hỗ trợ làm mới thủ công. Đây là đồng bộ khi đọc
API `/mine`, `/reports`, CSV; không có worker nền, không giữ token người dùng để chạy nền.

Endpoint mới `/api/v1/attendance/leave-calendar?employeeId=...&from=...&until=...`
trả số unit phép theo từng ngày/buổi (0, 1 hoặc 2), chỉ chủ hồ sơ hoặc HR/ADMIN đọc được.
Dùng ca theo ngày hiệu lực/ca đã ghi công, loại ngày lễ chung và trước ngày vào làm.
Endpoint này không gọi Leave để tránh vòng lặp REST.

Các API sau dành riêng HR/ADMIN, tháng dạng `YYYY-MM`:

| API | Ý nghĩa |
| --- | --- |
| `GET /api/v1/attendance/periods/{month}` | Trạng thái và version; tháng chưa tạo có version -1 |
| `POST /api/v1/attendance/periods/{month}/close` | Chốt toàn công ty; body `{ "reason": "..." }`, header `If-Match: "<version>"` |
| `POST /api/v1/attendance/periods/{month}/reopen` | Mở lại, cùng body/header, không xóa revision cũ |
| `GET /api/v1/attendance/periods/{month}/history` | Lịch sử version, người, thời điểm, lý do |
| `GET /api/v1/attendance/periods/{month}/check-sources` | So phép/ngày nghỉ hiện tại với lúc chốt, trả `changed`, `checkedAt` |

Chỉ chốt tháng đã kết thúc; chặn đơn phép/điều chỉnh phép/chấm công/correction/OT
PENDING, OT đã duyệt thiếu giờ, công chưa đầy đủ và xung đột nguồn/đơn đi trễ.
Thừa hưởng giới hạn 5.000 dòng báo cáo. Các thao tác chốt/mở và mutation Attendance
cùng khóa schedule state để tuần tự hóa, dùng If-Match chống ghi đè. Kỳ đóng chặn
chấm công, correction, xét duyệt đơn/OT, refresh và phân lịch ảnh hưởng kỳ đó.

Báo cáo và CSV kỳ đóng đọc bản chụp FINAL, lọc quyền nhân viên/phòng ban trên bản chụp;
không cần gọi dịch vụ nguồn. Mỗi lần chốt giữ snapshot trong lịch sử, mở lại không xóa.
Phép/ngày lễ sửa sau chốt không thay báo cáo FINAL: kiểm tra nguồn rồi mở lại/chốt lại.
Chốt kiểm tra nguồn hai lần và từ chối nếu khác nhau, nhưng không phải transaction
phân tán; thay đổi commit sau lần đọc cuối được phát hiện ở thao tác kiểm tra nguồn.
Lịch sử thu hồi/điều chỉnh Leave được giữ ở Leave; Attendance không ghi xuyên database.

## Kiểm tra phiên đăng nhập

Mỗi tài khoản chỉ có một phiên; đăng nhập mới thay phiên cũ. Service vẫn xác minh
chữ ký/hạn JWT cục bộ, sau đó gọi `GET /api/v1/auth/session` với Bearer token để
kiểm tra phiên tại Auth. Cấu hình `AUTH_SERVICE_URL` trỏ **trực tiếp Auth**, không
trỏ Gateway: local `http://localhost:8081`, Docker `http://auth:8081`.
Khởi động/health không yêu cầu Auth sẵn sàng; API có xác thực cần kết nối Auth.
Phiên bị thu hồi trả 401; Auth lỗi/timeout trả 503, không tiếp tục nghiệp vụ.
Không cache kết quả kiểm tra; các test nghiệp vụ mock `SessionVerifier`, test
`SessionVerifierTests` (ở resource service) kiểm tra HTTP contract riêng.
Đổi role chưa cập nhật claim trong JWT cũ; login/refresh cấp claim mới.

## Cấu hình và chạy độc lập

Project Maven riêng, Java 21, cổng mặc định 8085. Build/chạy từ thư mục
attendance-service; không cần chạy Maven ở gốc repository hoặc build service khác.

Điều kiện: PostgreSQL/schema và access public key để khởi động. Nghiệp vụ chấm công cần Employee, Leave, Calendar qua `REST`; cấu hình ba URL nguồn. `HRM_NOTIFICATIONS_ENABLED=false` khi không dùng Kafka.

1. Chuẩn bị database/user riêng và SQL theo phần database trong `README` này.
2. Tạo `.env` từ [.env.example](.env.example) nếu chưa có, rồi sửa host/port,
   mật khẩu, đường dẫn khóa và các cờ tính năng theo môi trường thực tế.
3. Nạp biến vào đúng terminal hoặc Run Configuration của IDE trước khi chạy.

Các lệnh sau dùng Git Bash/Bash, bắt đầu từ gốc repository:

```bash
cd back-end/modules/attendance-service
test -f .env || cp .env.example .env
# Sửa và lưu .env trước khi chạy các lệnh tiếp theo.
set -a
source ./.env
set +a
mvn spring-boot:run
```

Maven/Spring Boot không tự đọc .env. IDE cần working directory là project và
biến môi trường riêng; thay `.env` phải nạp lại và restart project đó. Mẫu dùng
bản sao access public key tại `keys/access-public.pem` của chính service, tính
từ working directory của module. Cấp cùng public key của môi trường Auth đang dùng
cho từng service khi triển khai; service không cần thư mục Auth trên cùng máy.

| Biến môi trường                             | Cấu hình cho project này                                                                  |
| ------------------------------------------- | ----------------------------------------------------------------------------------------- |
| `SERVER_PORT`                               | 8085                                                                                      |
| `SPRING_DATASOURCE_URL`                     | jdbc:postgresql://localhost:5432/attendance_db; sửa port nếu DB dùng 55432 hoặc port khác |
| `SPRING_DATASOURCE_USERNAME`                | attendance_user                                                                           |
| `SPRING_DATASOURCE_PASSWORD`                | Mật khẩu của user database này                                                            |
| `JWT_ACCESS_PUBLIC_KEY`                     | file:/duong-dan-tuyet-doi/access-public.pem                                               |
| `JWT_ACCESS_ISSUER` / `JWT_ACCESS_AUDIENCE` | auth-service / hrm-api-access, khớp Auth                                                  |

Chỉ cần access public key của Auth; không cấp private key cho project này.

`SPRING_DATASOURCE_*` ưu tiên hơn biến `<SERVICE>_DB_*`; `SERVER_PORT` ưu tiên hơn
biến cổng riêng nếu có. Không giữ hai bộ giá trị mâu thuẫn trong environment.
Dừng bằng `Ctrl+C` trong terminal này. Build riêng bằng `mvn package`; kiểm thử theo
phần kiểm thử bên dưới. Với backend có wrapper có thể dùng `./mvnw` hoặc `mvnw.cmd`
thay mvn. Không bắt buộc `run-all.sh` hoặc Docker Compose để chạy project.

## Phạm vi đã triển khai

- HR/ADMIN quản lý mẫu ca `FIXED_SHIFT`, phiên bản và lịch sử; vô hiệu hóa thay vì xóa lịch sử.
- Phân công có ngày hiệu lực, ngày trong tuần, xem trước tác động và lịch sử. `COMPANY_DEFAULT` giữ lịch riêng; `SELECTED_EMPLOYEES` áp lịch riêng; `ALL_EMPLOYEES` thay cả lịch riêng trong phạm vi ngày/weekday đã chọn.
- Đơn đi trễ/về sớm: gửi/sửa/rút PENDING, HR/ADMIN xét duyệt, lịch sử; kết nối UI `/attendance/requests`. Đơn duyệt ghi nhận có phép nhưng vẫn trừ công.
- Đơn bổ sung/điều chỉnh: gửi/sửa/rút đơn chờ, HR/ADMIN duyệt/từ chối; giờ đã duyệt dùng tính công, giờ và sự kiện gốc được giữ nguyên.
- Nhân viên tự check-in/out bằng thời gian server, một lần vào/ra mỗi ngày qua mạng công ty. Employee phải ACTIVE/PROBATION và đã tới ngày vào làm.
- Bảng công cá nhân, báo cáo HR/ADMIN, lọc nhân viên/phòng ban/ngày, CSV UTF-8 BOM cùng quy tắc tính với API.
- Kết hợp phép đã duyệt qua Leave và ngày nghỉ chung đã công bố qua Calendar. Chỉ đọc REST với Bearer token của người gọi, không truy vấn chéo database.
- Tham chiếu phiên bản ca bất biến, snapshot nhân viên/phép/ngày nghỉ tối thiểu; ghi sự kiện, kết quả và idempotency trong cùng transaction. Phiên bản `If-Match` chống ghi đè; khóa DB bảo vệ phân công/check-in đồng thời.

Chưa gồm `FLEXIBLE_DURATION`, nhiều lần vào/ra trong một ca,
payroll, tự đồng bộ Kafka hoặc thay đổi sổ số dư Leave. Đã có chốt/mở tháng và đồng bộ coverage khi đọc.
**File CSV rút gọn:** nút xuất trên UI mặc định trả 16 cột tiếng Việt, mỗi nhân viên/ngày một dòng:
Mã nhân viên, Họ tên, Ngày công, Giờ vào (GMT+7), Giờ ra (GMT+7), Công chuẩn (phút),
Công ghi nhận (phút), Phép năm (phút), Nghỉ không lương (phút), Đi trễ (phút),
Về sớm (phút), OT được duyệt (phút), OT ghi nhận (phút), Trạng thái chấm công,
Cần kiểm tra, Trạng thái bảng công. Các mốc vào/ra gồm cả ngày để tránh nhầm ca qua đêm.
Không xuất UUID nhân viên/phòng ban/ca/đơn, revision, thời điểm đồng bộ, giờ gốc hoặc
các cột giây trùng với số phút. Cột Cần kiểm tra ghi số đơn phép, trễ/sớm, OT còn chờ
và đơn trễ/sớm xung đột; không chứa ID hoặc lý do riêng tư.

Số phút giữ đúng kết quả tính hiện hành: công thường tách khỏi OT, trễ/sớm là số phút
đã làm tròn theo quy tắc hiện tại (kể cả đã xin phép), không suy ra tiền lương hoặc
quy đổi ngày công theo mức 8 giờ cố định. Công chưa xác định để ô trống, không đổi thành 0.
Bảng kỳ mở ghi **Tạm tính - chưa chốt**, kỳ đóng dùng bản chụp **Đã chốt**. CSV mở được bằng Excel, chưa phải định dạng `.xlsx`.
Nếu cần các cột kỹ thuật để đối soát, dùng cùng endpoint với `detail=true`; bản đầy đủ
40 cột cũ vẫn được giữ. JSON API không đổi.

Báo cáo luôn `reportState=DRAFT`; không phải bảng công đã chốt. Khóa ghi hiện dùng
một hàng điều phối chung; cần đánh giá tải trước khi mở rộng quy mô.

## Chính sách chế độ chấm công

- **Hiện tại ưu tiên `FIXED_SHIFT`**: ca có giờ bắt đầu/kết thúc xác định, chẳng
  hạn 08:00–17:00 hoặc 08:00–17:30, cấu hình riêng các khoảng nghỉ. Ca part-time
  cố định như 13:00–17:00 cũng dùng chế độ này.
- **`FLEXIBLE_DURATION` để giai đoạn sau**: tính theo thời lượng phải làm, thuộc
  nhóm tính năng nâng cao và mặc định ẩn. Cơ chế bật tính năng và quyền truy cập
  sẽ được thiết kế khi triển khai; hiện không có quyền mở khóa riêng cho ADMIN.
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
  xuất bản rút gọn từ cùng kết quả tính của API; `detail=true` giữ bản đối soát đầy đủ.
  Cả hai giữ BOM và escape công thức CSV.
- [WorkTimeSupport](#worktime) cung cấp các hàm nội bộ
  `calculate(input)` và `calculatePeriod(inputs)` để tính công hưởng lương từ
  tham số. Helper và kiểm thử nằm trong module Attendance, không có POM/dependency
  riêng. API/UI hiện hành chưa sử dụng các trường kết quả mở rộng này.

[Schema nghiệp vụ chính](docs/sql/001_attendance_schema.sql) khai báo bảng, index,
FK và trigger cho chấm công, bao gồm bảng `notification_outbox`. Database cũ thiếu
bảng này cần áp [004_notification_outbox.sql](docs/sql/004_notification_outbox.sql),
kể cả khi tắt thông báo, vì Hibernate kiểm tra entity khi khởi động. Chưa có công cụ
migration tự động; database có dữ liệu cần kế hoạch nâng cấp riêng. Chỉ tạo lại
database phát triển khi chấp nhận mất dữ liệu, theo [hướng dẫn SQL](#database).

Idempotency mặc định giữ payload trả về của `CHECK_IN`, `CHECK_OUT`, `OT_CHECK_IN`,
`OT_CHECK_OUT` trong 180 ngày. Job chạy 03:00 giờ Việt Nam, tối đa 500 payload/lần;
chỉ đặt `response_body=null`, giữ khóa và yêu cầu gốc. Gửi lại khóa đã hết payload
trả HTTP 409, không thực hiện lại nghiệp vụ. `APPLY_SCHEDULE`, `DECIDE_OT`,
`REFRESH_COVERAGE` và các lịch sử nghiệp vụ không bị dọn. Cấu hình bằng
`ATTENDANCE_IDEMPOTENCY_RETENTION_DAYS` (0 để tắt) và
`ATTENDANCE_IDEMPOTENCY_CLEANUP_CRON` (`-` để tắt lịch chạy).

## Chạy local

Yêu cầu JDK 21, PostgreSQL, public key Auth và các service Employee/Leave/Calendar.
Tạo user/database riêng rồi áp [schema SQL](#database) trước khi chạy.
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

| Biến                                            | Mặc định local                                               |
| ----------------------------------------------- | ------------------------------------------------------------ |
| `ATTENDANCE_PORT`                               | `8085`                                                       |
| `ATTENDANCE_DB_URL`                             | `jdbc:postgresql://localhost:5432/attendance_db`             |
| `ATTENDANCE_DB_USER` / `ATTENDANCE_DB_PASSWORD` | `attendance_user` / `attendance_password` (chỉ local)        |
| `JWT_ACCESS_PUBLIC_KEY`                         | `file:./keys/access-public.pem`           |
| `JWT_ACCESS_ISSUER` / `JWT_ACCESS_AUDIENCE`     | `auth-service` / `hrm-api-access`                            |
| `EMPLOYEE_SERVICE_URL`                          | `http://localhost:8082`                                      |
| `LEAVE_SERVICE_URL`                             | `http://localhost:8084`                                      |
| `CALENDAR_SERVICE_URL`                          | `http://localhost:8083`                                      |
| `ATTENDANCE_ALLOWED_NETWORKS`                   | `127.0.0.1/32,::1/128,113.161.73.224/32,192.168.1.5/32`      |
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
| `GET /reports/export.csv`                             | HR/ADMIN; cùng bộ lọc; mặc định rút gọn, `detail=true` để đối soát đầy đủ |
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

Ngày đã check-in giữ ca và giờ gốc; coverage phép/ngày nghỉ của kỳ mở tự cập nhật
khi đọc báo cáo. HR vẫn có **Cập nhật phép/lịch** với version/audit. Kỳ đóng giữ
bản chụp FINAL; cần mở lại trước khi thay đổi. Không có transaction xuyên service.

Frontend đã kết nối API thật, dùng phiên đăng nhập sẵn có:

| Trang                                          | Đường dẫn                    |
| ---------------------------------------------- | ---------------------------- |
| Công của tôi: giờ vào/ra, ca hôm nay, lịch sử  | `/attendance`                |
| Đơn đi trễ/về sớm cá nhân                      | `/attendance/requests`       |
| Hàng chờ duyệt đi trễ/về sớm (HR/ADMIN)        | `/attendance/requests/inbox` |
| Bổ sung/điều chỉnh công; duyệt cho HR/ADMIN    | `/attendance/corrections`    |
| Đăng ký/chấm giờ OT; duyệt cho HR/ADMIN        | `/attendance/overtime`       |
| Ca làm việc: tạo/sửa/vô hiệu hóa/lịch sử       | `/attendance/shifts`         |
| Phân công: phạm vi, hiệu lực, preview, lịch sử | `/attendance/schedules`      |
| Bảng công: bộ lọc, đối soát, tải CSV           | `/attendance/reports`        |

Các trang quản lý ca, phân công, báo cáo và hàng chờ đi trễ/về sớm chỉ dành HR/ADMIN;
API vẫn tự kiểm tra quyền. OT/điều chỉnh có phần cá nhân và phần xét duyệt theo role.

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

Contract, ví dụ đối soát và UI: [Requests UI/API](../../../frontend/README.md#requests).
Các route đều dưới `/api/v1/attendance/requests`, dùng JWT hiện tại. `GET /` của
người gọi; `GET /inbox` dành HR/ADMIN; `POST /` cần Idempotency-Key; `PUT /{id}`,
`POST /{id}/cancel`, `POST /{id}/decision` dùng If-Match. GET chi tiết/history chỉ
chủ đơn hoặc HR/ADMIN. Không tự duyệt, không sửa/rút đơn đã xử lý.

Yêu cầu ngày trong 365 ngày tới, giờ trong buổi của ca thực tế, reason 1–1.000 ký tự.
Snapshot ca/nhân viên và thời lượng server tính; lịch thay đổi phải cập nhật đơn chờ
trước khi duyệt. Ca không được thay đổi nếu ảnh hưởng đơn APPROVED. Kiểm tra trùng
và xét duyệt chạy dưới cùng khóa DB với phân ca/check-in, audit cùng transaction.
Trùng slot PENDING/APPROVED còn có unique constraint tại database.

Bảng công và CSV đối soát (`detail=true`) thêm permissionCoverage/nhóm cột request: không lộ reason/reviewNote.
Phút công vẫn là phần phải làm trừ trễ/sớm làm tròn 15 phút; đơn không tạo sự kiện
vào/ra, không cộng công và không thay số dư Leave. Phần được phép chỉ bao phủ phút
thiếu thực tế nằm trong khoảng xin phép; không bù phần vượt giờ đã xin.
Thiếu punch không suy ra giờ thực tế. Khi phép/ngày nghỉ mới xung đột với đơn duyệt,
sau refresh nguồn, ID đơn nằm trong conflictingRequestIds để HR đối soát.

Phê duyệt trong cùng database có hiệu lực ở lần đọc báo cáo kỳ mở tiếp theo (DRAFT);
kỳ đóng trả bản chụp FINAL. Leave/Calendar kiểm tra qua REST chưa có transaction xuyên service: nếu
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

| API                             | Chức năng                                                            |
| ------------------------------- | -------------------------------------------------------------------- |
| `GET /overtime`                 | Danh sách đơn của mình; lọc `status`, `page`, `size`                 |
| `GET /overtime/inbox`           | HR/Admin xem đơn cần xử lý                                           |
| `POST /overtime`                | Gửi đơn, bắt buộc `Idempotency-Key`                                  |
| `POST /overtime/{id}/cancel`    | Chủ đơn rút PENDING, dùng `If-Match`                                 |
| `POST /overtime/{id}/decision`  | HR/Admin duyệt/từ chối, dùng `If-Match`; không tự duyệt              |
| `POST /overtime/{id}/check-in`  | Chủ đơn bắt đầu OT đã duyệt qua mạng công ty, dùng `Idempotency-Key` |
| `POST /overtime/{id}/check-out` | Kết thúc OT qua mạng công ty, dùng `Idempotency-Key`                 |

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
`approvedIds`. Tổng `countedMinutes` chỉ cộng đơn hoàn tất. CSV đối soát (`detail=true`) có
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

## Đơn bổ sung / điều chỉnh chấm công

Trang `/attendance/corrections`: nhân viên chọn ngày công, nhập đủ giờ vào/ra thực tế
và lý do (1–1.000 ký tự). Liên kết từ lịch sử công mở sẵn ngày cần điều chỉnh.
Giờ nhập theo `Asia/Ho_Chi_Minh`, chính xác đến giây; ca qua đêm dùng ngày bắt đầu ca,
giờ ra thuộc ngày sau. Chỉ gửi và duyệt sau khi ca kết thúc; không nhận giờ tương lai,
giờ ngoài cửa sổ ghi nhận, giờ đảo thứ tự hoặc cặp giờ không thay đổi.

- Một đơn `PENDING` cho mỗi nhân viên/ngày; chủ đơn được sửa hoặc rút.
- HR/ADMIN duyệt/từ chối, không tự xét duyệt; từ chối bắt buộc lý do.
- Khi duyệt, kiểm tra lại điều kiện nhân viên, phiên bản ca/ngày công và phép/ngày nghỉ
  mới nhất. Xung đột nguồn chặn duyệt. Nếu ngày công hoặc ca đã đổi, trả 412;
  chủ đơn cần mở **Sửa đơn** để tải dữ liệu hiện tại rồi lưu lại trước khi HR duyệt.
- `APPROVED` cập nhật cặp giờ hiệu lực trong cùng transaction với lịch sử đơn.
  Có thể gửi đơn mới để thay thế lần điều chỉnh đã duyệt; mỗi lần đều giữ lịch sử.
- `attendance_daily.check_in/check_out` và `attendance_events` là dữ liệu chấm gốc,
  không bị sửa hoặc tạo giả. `corrected_check_in/corrected_check_out/correction_id`
  lưu cặp giờ hiệu lực và đơn đã duyệt mới nhất. Nếu quên cả hai mốc, duyệt tạo ngày
  công có giờ gốc null và không tạo sự kiện CHECK_IN/CHECK_OUT.
- Bảng công/API/CSV dùng giờ hiệu lực để tính công và đối soát đi trễ/về sớm;
  API và CSV đối soát (`detail=true`) giữ `originalCheckIn`, `originalCheckOut`,
  `correctionId` (CSV dùng snake_case). Bản rút gọn chỉ xuất giờ hiệu lực.
  Lý do và ghi chú xét duyệt chỉ có trong đơn/lịch sử được kiểm tra quyền.
  OT vẫn chấm riêng; đơn điều chỉnh này không sửa OT hoặc chốt bảng công.
- Phép/ngày nghỉ mới nhất được chụp lại khi duyệt; nguồn thay đổi sau đó vẫn dùng
  coverage mới ở lần tải báo cáo kỳ mở tiếp theo. Báo cáo kỳ đã chốt giữ FINAL.

API dưới `/api/v1/attendance/corrections`:

| Method / đường dẫn    | Chức năng                                 |
| --------------------- | ----------------------------------------- |
| `GET /`               | Đơn của mình; `status`, `page`, `size`    |
| `GET /inbox`          | HR/ADMIN; lọc và phân trang               |
| `GET /{id}`           | Chi tiết; chủ đơn hoặc HR/ADMIN           |
| `GET /{id}/history`   | Lịch sử có snapshot trước/sau, phân trang |
| `POST /`              | Gửi đơn; `Idempotency-Key`                |
| `PUT /{id}`           | Chủ đơn sửa PENDING; `If-Match`           |
| `POST /{id}/cancel`   | Chủ đơn rút PENDING; `If-Match`           |
| `POST /{id}/decision` | HR/ADMIN duyệt/từ chối; `If-Match`        |

Body tạo/sửa: `workDate`, `shiftId`, `shiftVersion`, `recordVersion` (null khi chưa
có ngày công), `proposedCheckIn`, `proposedCheckOut` (ISO-8601 có offset), `reason`.
Lấy phiên bản ca từ API lịch cá nhân, phiên bản ngày công từ API `/mine`.
Body xét duyệt: `status` (`APPROVED`/`REJECTED`), `reviewNote`.
Tạo đơn dùng idempotency; thay đổi dùng optimistic version và khóa điều phối DB.
Schema chính bổ sung hai bảng `attendance_corrections`, `attendance_correction_history`;
áp trên database mới theo [quy trình database](#database).

<a id="database"></a>

## Attendance database

Attendance dùng PostgreSQL riêng `attendance_db`, JPA `ddl-auto=validate`, không
query/FK chéo database. Schema nghiệp vụ chính:
[001_attendance_schema.sql](docs/sql/001_attendance_schema.sql), đã bao gồm outbox.
Database cũ thiếu `notification_outbox` cần áp
[004_notification_outbox.sql](docs/sql/004_notification_outbox.sql) trước khi khởi động,
kể cả khi `HRM_NOTIFICATIONS_ENABLED=false`; xem [Notification](../notification-service/README.md).

`NotificationOutbox` lưu sự kiện qua `NotificationOutboxRepository` trong cùng
transaction JPA với đơn và lịch sử nghiệp vụ. Publisher dùng native query
`FOR UPDATE SKIP LOCKED` để giữ khóa từng sự kiện; JPA cập nhật entity khi commit.
Gửi lỗi được ghi log và retry sau 10 giây, mỗi lượt xử lý tối đa 20 sự kiện.
Thời điểm sự kiện, kiểm tra đến hạn và retry dùng chung `Clock` của Attendance.
Kafka có thể nhận lại sự kiện nếu gửi thành công nhưng commit trạng thái thất bại;
consumer phải giữ cơ chế chống trùng theo `eventId`.
Lệnh `psql` bên dưới dùng cổng mặc định `5432`; thêm `-p 55432` nếu dùng cluster trong [hướng dẫn chạy độc lập](../../../README.md#local).

Trong giai đoạn phát triển, sửa cấu trúc trực tiếp trong file này rồi tạo lại
database. Không cần chạy các file migration theo thứ tự. File dùng `CREATE IF NOT
EXISTS` để có thể chạy lại trên cùng schema và giữ seed duy nhất; nó không chuyển
đổi cấu trúc database cũ.

<a id="database-tạo-mới"></a>

### Tạo mới

Tạo user/database bằng tài khoản quản trị PostgreSQL (mật khẩu ví dụ chỉ dùng local):

```sql
CREATE USER attendance_user WITH PASSWORD 'attendance_password';
CREATE DATABASE attendance_db OWNER attendance_user;
```

Nếu đã có user thì chỉ tạo database. Từ thư mục `attendance-service`, chạy:

```bash
psql -h localhost -U attendance_user -d attendance_db -v ON_ERROR_STOP=1 -f docs/sql/001_attendance_schema.sql
```

Có thể mở file trong DBeaver, chọn kết nối `attendance_db` bằng `attendance_user`
và chạy **toàn bộ script**, gồm cả `BEGIN`, function/trigger và `COMMIT`.

<a id="database-tạo-lại-database-khi-phát-triển"></a>

### Tạo lại database khi phát triển

Dừng Attendance, đóng các kết nối tới `attendance_db`. Trên kết nối quản trị tới
`postgres`, chạy từng lệnh dưới đây ngoài transaction. Thao tác này xóa toàn bộ
dữ liệu Attendance; nếu cần giữ dữ liệu thì xuất bản sao lưu trước.

```sql
DROP DATABASE IF EXISTS attendance_db;
CREATE DATABASE attendance_db OWNER attendance_user;
```

Sau đó chạy lại `001_attendance_schema.sql` bằng lệnh phía trên rồi khởi động backend.
Schema này đã bao gồm outbox; script không tự xóa database.

Compose chỉ mount file này vào `docker-entrypoint-initdb.d`, tự chạy với volume
PostgreSQL mới. Với database trống đã tạo lại trong Compose, từ `infra/gateway`:

```bash
docker compose exec -T attendance-db psql -U attendance_user -d attendance_db -v ON_ERROR_STOP=1 < ../../attendance-service/docs/sql/001_attendance_schema.sql
```

<a id="database-nội-dung-schema"></a>

### Nội dung schema

- 13 bảng cho mẫu ca/phiên bản, phân công, ngày công, đơn đi trễ/về sớm, OT,
  đơn bổ sung/điều chỉnh, lịch sử và idempotency.
- FK `(shift_id, shift_version)` từ ngày công, phân công và đơn tới revision;
  không lưu JSON ca lặp trong các bảng này. Trigger chặn UPDATE revision, FK
  ngăn xóa revision đang được sử dụng.
- Unique nhân viên/ngày, ngày công/loại sự kiện và actor/type/idempotency key;
  index phục vụ báo cáo theo khoảng ngày, lịch sử và kiểm tra OT trùng giờ.
- `response_body` của operations cho phép null để dọn payload vào/ra hết hạn;
  khóa chống trùng vẫn được giữ. Cấu hình job trong README service.
- Seed hàng điều phối và mẫu ca UUID `00000000-0000-0000-0000-000000000001`,
  giờ `08:00–12:00, 13:30–17:30`, cửa sổ chấm công `06:00–22:00`.
  Khi backend khởi động, `DefaultScheduleInitializer` áp lịch thứ Hai–thứ Sáu
  từ ngày khởi tạo, không ngày kết thúc, nếu chưa có lịch mặc định công ty.

<a id="database-kiểm-thử"></a>

### Kiểm thử

`./mvnw test` đọc schema chính; test H2 bỏ khối được đánh dấu `POSTGRESQL ONLY`
cho partial index và trigger, không duy trì thêm file schema SQL riêng.

Kiểm tra đầy đủ trên PostgreSQL, dùng một schema thử riêng và tự xóa sau khi chạy:

```bash
# Cấu hình PGHOST, PGPORT, PGUSER, PGDATABASE; xác thực qua PGPASSWORD hoặc .pgpass.
scripts/verify-schema.sh
```

Script kiểm tra tạo đủ bảng, seed không trùng khi chạy lại, FK, revision bất biến
và unique sự kiện chấm công. [Contract và cấu hình service](README.md).

<a id="worktime"></a>

## Helper tính công trong Attendance

`WorkTimeSupport.calculate(input)` là hàm hỗ trợ nội bộ của `attendance-service`,
thuộc package `com.company.attendance.service.worktime`. Helper nhận tham số rõ
ràng, không truy cập database/API hay đồng hồ hệ thống. Không có Maven module,
JAR phát hành hoặc dependency riêng; code và test được build cùng Attendance.

API/UI hiện tại vẫn dùng `AttendanceCalculator`; việc đưa các trường công hưởng
lương từ helper vào báo cáo API/UI chưa được triển khai. Việc chuyển code vào
service không thay đổi công thức hay contract của các endpoint hiện hành.

<a id="worktime-build-và-sử-dụng"></a>

### Build và sử dụng

Từ thư mục `back-end/modules/attendance-service`:

```bash
./mvnw test
# Hoặc chỉ kiểm thử helper
./mvnw -Dtest=WorkTimeSupportTest test
```

Ví dụ được kiểm tra trong [WorkTimeSupportTest](src/test/java/com/company/attendance/service/worktime/WorkTimeSupportTest.java), gồm nghỉ phép năm cả/nửa ngày, ngày lễ và OT. Chạy lệnh kiểm thử helper ở trên để đối chiếu kết quả.

Kết quả nghỉ phép năm cả ngày trên ca 8 giờ:

```text
status=ON_LEAVE
workedMinutes=0
annualLeaveMinutes=480
payableMinutes=480
payableDays=1.000000
```

<a id="worktime-api"></a>

### API

```java
WorkTimeResult day = WorkTimeSupport.calculate(input);
WorkTimePeriodResult period = WorkTimeSupport.calculatePeriod(List.of(inputDay1, inputDay2));
```

| Tham số `WorkTimeInput` | Ý nghĩa                                                                                              |
| ----------------------- | ---------------------------------------------------------------------------------------------------- |
| `workDate`, `zone`      | Ngày bắt đầu ca và múi giờ nghiệp vụ                                                                 |
| `schedule`              | Các khoảng làm việc theo thứ tự, không trùng, tên buổi duy nhất; danh sách rỗng là không có lịch làm |
| `calendarDay`           | `WORKDAY` hoặc `PAID_HOLIDAY` đã được xác minh là ngày nghỉ hưởng lương áp dụng cho nhân viên        |
| `leaves`                | Các đơn phép thuộc nhân viên/ngày này; `FULL_DAY` hoặc tên buổi khớp ca                              |
| `attendance`            | Check-in/out thực tế dạng `Instant`; `Punch.none()` khi chưa có ghi nhận                             |
| `overtime`              | Đơn OT, trạng thái duyệt và cặp giờ vào/ra OT riêng                                                  |
| `recordingClosesAt`     | Thời điểm hết cửa sổ chấm công, dùng phân biệt đang làm và thiếu giờ ra                              |
| `now`                   | Mốc thời gian tính báo cáo; truyền rõ để cùng input luôn cho cùng kết quả                            |
| `policy`                | Chuẩn phút/ngày, bước làm tròn trễ/sớm và mẫu số quy đổi công                                        |

`WorkTimePolicy.standard()` dùng chuẩn 480 phút/ngày, trễ và sớm làm tròn riêng lên
15 phút, quy đổi theo `STANDARD_DAY`. Có thể dùng `ASSIGNED_SHIFT` nếu chính sách
coi làm đủ một ca part-time là một công. Ví dụ ca 4 giờ đầy đủ tương ứng 0,5 công
với `STANDARD_DAY`, hoặc 1 công với `ASSIGNED_SHIFT`. Đây là tham số nghiệp vụ,
không phải quy định pháp luật hay công thức tiền lương bắt buộc.

Giờ ca/OT dự kiến phải chính xác đến phút. Giờ thực tế giữ số giây (bỏ phần lẻ
nhỏ hơn một giây khi tính thời lượng). Ca qua đêm dùng `Instant` của ngày hôm sau
cho giờ kết thúc; ca dài tối đa 24 giờ. OT thuộc ngày bắt đầu OT.

<a id="worktime-kết-quả-và-quy-tắc"></a>

### Kết quả và quy tắc

- `scheduledMinutes`: toàn bộ thời lượng ca, giữ nguyên cả vào ngày nghỉ Calendar.
- `requiredWorkMinutes`: phần ca còn phải làm sau khi áp dụng Calendar và phép APPROVED.
- `workedActualSeconds`: thời gian thực tế giao với các khoảng phải làm; loại giờ nghỉ giữa ca.
- `workedCountedMinutes`: phút phải làm trừ trễ và sớm đã làm tròn riêng, tối thiểu 0.
  Vào sớm/ở lại muộn không bù đi trễ và không tự tạo OT.
- `paidHolidayMinutes`, `annualLeaveMinutes`, `otherPaidLeaveMinutes`, `unpaidLeaveMinutes`:
  tách từng loại nghỉ. Phép PENDING/REJECTED/CANCELLED không giảm giờ phải làm.
- `payableMinutes = workedCountedMinutes + paidHolidayMinutes + annualLeaveMinutes + otherPaidLeaveMinutes`.
  Nghỉ không lương không được cộng.
- `workedDays`, `paidAbsenceDays`, `payableDays`: công quy đổi theo policy, 6 chữ số thập phân.
  Đây là công hưởng lương, **không phải số ngày trừ quỹ phép**. Leave Service vẫn sở hữu
  quỹ phép và chính sách nửa ngày/cả ngày; helper không ghi sổ số dư.
- Ngày nghỉ Calendar ưu tiên hơn phép; không áp dụng/trừ phút phép trên ngày đó.
  Ngày không có lịch làm không tự phát sinh công hay phút phép dù có Calendar/đơn phép.
  Không thể suy ra ngày nghỉ chỉ từ thứ Bảy/Chủ nhật: caller phải truyền lịch thực tế.
- Thiếu check-in/out, giờ đảo ngược hoặc dữ liệu phép xung đột: số công chưa xác định là
  `null`, không thay bằng 0. Phép có lương đã biết vẫn hiển thị riêng ở các trường phút nghỉ.
- Chỉ OT APPROVED mới tính; số phút OT là phần giao giờ thực tế/giờ đã duyệt, làm tròn
  xuống phút. OT không cộng vào công thường và không tính hệ số lương. OT thiếu giờ
  ghi nhận trả `null`, OT chưa duyệt trả 0. Có thể có OT riêng vào ngày nghỉ hưởng lương.
- `issues` và trạng thái chỉ rõ xung đột; `complete()` yêu cầu cả công thường và OT
  có kết quả. `status` ở cấp ngày mô tả công thường; mỗi OT có trạng thái riêng.

Tổng hợp kỳ chỉ nhận dữ liệu của **một nhân viên**, cùng policy/múi giờ, mỗi ngày một
lần. `knownPayableMinutes` là tổng phần đã xác định; `payableMinutes/payableDays`
trả `null` nếu còn ngày công chưa rõ. `incompleteDates` liệt kê ngày thiếu công/OT.
Với `STANDARD_DAY`, tổng công kỳ được chia từ tổng phút một lần; với `ASSIGNED_SHIFT`
cộng số công từng ca đã quy đổi. Kỳ rỗng trả 0. Lịch/OT chồng giờ giữa hai ngày,
ngày lặp hoặc mã OT lặp bị từ chối để tránh cộng trùng.

<a id="worktime-trách-nhiệm-bên-gọi"></a>

### Trách nhiệm bên gọi

Caller xác định nhân viên đủ điều kiện, lịch riêng/lịch mặc định có hiệu lực,
Calendar đã công bố và áp dụng, phép thuộc đúng ngày, cũng như quyền/ngày giờ duyệt
OT. Cung cấp snapshot nếu cần tái lập báo cáo. Helper không xác thực quyền, mạng
chấm công, không duyệt đơn hoặc thay dữ liệu nguồn. Khi tính một ngày riêng lẻ,
caller kiểm tra cả lịch/OT ngày liền kề; `calculatePeriod` tự kiểm tra chồng giờ
qua ngày trong tập input được truyền.

Đơn Late/Early có phép không tự bù công theo chính sách hiện tại nên không thay đổi
kết quả phút công của helper. Phân loại sai lệch có phép/chưa có phép vẫn do
Attendance đối soát. Việc sửa Leave để tính số ngày trừ quỹ theo Calendar/lịch,
nối kết quả vào UI/CSV và khóa kỳ là các bước tích hợp riêng.

<a id="design"></a>

## Thiết kế chung: lịch làm việc, nghỉ phép và chấm công

<a id="design-trạng-thái-và-phạm-vi"></a>

### Trạng thái và phạm vi

Tài liệu gồm thiết kế tổng thể và lộ trình mở rộng. [Attendance Service](README.md)
đã triển khai Java 21/JPA, ca cố định, phân công theo hiệu lực, check-in/out qua mạng
công ty, tổng hợp phép/ngày nghỉ và CSV tạm tính; có UI tương ứng. Mẫu mặc định
08:00–12:00, 13:30–17:30 tự áp dụng thứ Hai–thứ Sáu khi service khởi động nếu
công ty chưa có lịch. Lịch riêng và lịch công ty HR đã cấu hình được giữ nguyên.
Attendance cũng quản lý đơn đi trễ/về sớm, ca cố định qua đêm và OT có duyệt.
Leave hỗ trợ bước 0,5 ngày và endpoint coverage tối thiểu phục vụ Attendance.
Đã có đơn bổ sung/điều chỉnh công, duyệt và chốt/mở tháng. Chưa có lịch linh hoạt
hoặc tự đồng bộ nguồn. Các mô hình/contract dự kiến bên dưới không đồng nghĩa đã
triển khai toàn bộ; API hiện hành nằm trong README service.

Attendance hiện dùng lớp cha JPA chung cho metadata audit, version và xét duyệt;
giữ các bảng nghiệp vụ/lịch sử riêng để bảo toàn FK và snapshot. Báo cáo và lịch
cá nhân đọc dữ liệu theo khoảng ngày, tránh truy vấn từng ngày. Xem
[kiến trúc hiện hành](#kiến-trúc-code-và-dữ-liệu)
và [schema nghiệp vụ chính](docs/sql/001_attendance_schema.sql).
Ngày công, quy tắc lịch và đơn đi trễ/về sớm tham chiếu phiên bản ca bất biến thay
vì sao chép JSON ca. Snapshot nguồn được thu gọn theo từng ngày; vẫn giữ bằng
chứng chấm công từng ngày và ràng buộc chống ghi trùng vào/ra. Trong giai đoạn
phát triển, cập nhật schema này và tạo lại database khi thay đổi cấu trúc.

Thiết kế dùng chung cho giờ cố định, ca part-time và lịch linh hoạt chỉ yêu cầu
đủ thời lượng trong ngày. Trước mắt triển khai giờ cố định; các chế độ sau dùng
cùng mô hình dữ liệu. Ví dụ chính sách của nhiều công ty không đồng nghĩa triển
khai multi-tenant: phạm vi hiện tại vẫn là một công ty, `Asia/Ho_Chi_Minh`.

<a id="design-1-hai-chế-độ-tính-công-độc-lập-với-full-timepart-time"></a>

### 1. Hai chế độ tính công, độc lập với full-time/part-time

| Chế độ              | Điều kiện hoàn thành                    | Đi trễ/về sớm                           | Ví dụ                                                          |
| ------------------- | --------------------------------------- | --------------------------------------- | -------------------------------------------------------------- |
| `FIXED_SHIFT`       | Làm trong các khoảng giờ được phân công | Đánh giá theo đầu/cuối phần ca phải làm | Mặc định 08:00–12:00, 13:30–17:30; có thể cấu hình 13:00–15:00 |
| `FLEXIBLE_DURATION` | Đủ thời lượng yêu cầu trong ngày        | Không áp dụng                           | Đủ 8 giờ/ngày; hoặc part-time đủ 2,5 giờ/ngày                  |

Full-time/part-time mô tả lịch và thời lượng được phân công, không phải thuật
toán tính công thứ ba. Ca cố định 2 giờ dùng cùng thuật toán với ca cố định 8 giờ.
Lịch linh hoạt không cần khai báo một giờ bắt đầu/kết thúc giả để tính đi trễ.

Ưu tiên hiện tại là **`FIXED_SHIFT`**, phù hợp với lịch có khung giờ xác định,
kể cả ca part-time. **`FLEXIBLE_DURATION` thuộc giai đoạn nâng cao**, mặc định
ẩn. Cơ chế bật tính năng và quyền truy cập chờ thiết kế sau; không coi đây là
quyền riêng của ADMIN trong phiên bản hiện tại. Khi triển khai, việc bật tính năng
không tự chuyển các ca cố định đã có sang chế độ linh hoạt.

Đây là chính sách cho giai đoạn sau: hiện chưa triển khai cách tính công linh
hoạt hoặc chức năng mở khóa. UI chỉ dùng ca cố định và `ShiftService` vẫn từ chối
`FLEXIBLE_DURATION`, kể cả yêu cầu gửi bởi ADMIN.

Chính sách mặc định của công ty tạo phân công cho nhân viên. Cho phép phân công
riêng theo nhân viên và ngày hiệu lực; mỗi ngày phải xác định được duy nhất một
chính sách áp dụng. Không suy ra chế độ từ lần chấm công hoặc tự đổi chế độ khi
nhân viên đi trễ. Không có lịch hoặc lịch chồng lấn thì báo cần xử lý.

<a id="design-11-hradmin-tạo-ca-và-chọn-phạm-vi-áp-dụng"></a>

#### 1.1. HR/Admin tạo ca và chọn phạm vi áp dụng

HR và ADMIN được tạo, sửa phiên bản ca `FIXED_SHIFT` và phân công; backend
kiểm tra quyền cho từng thao tác. Nhân viên xem lịch được gán, không tự sửa ca.

Ca mặc định ban đầu:

| Phần lịch                  | Thời gian                | Thời lượng làm việc |
| -------------------------- | ------------------------ | ------------------- |
| `MORNING`                  | 08:00–12:00              | 240 phút            |
| Nghỉ trưa, không tính công | 12:00–13:30              | 0 phút              |
| `AFTERNOON`                | 13:30–17:30              | 240 phút            |
| Cả ngày                    | Hai khoảng làm việc trên | 480 phút / 8 giờ    |

Hai khoảng sáng/chiều thuộc **một ca/ngày**, không phải hai phân công hoặc hai
phiên chấm công. Giờ mặc định là dữ liệu cấu hình; không hard-code trong thuật
toán. HR/Admin có thể sửa từng khoảng hoặc tạo mẫu ca khác chỉ có một khoảng.

Ví dụ giữ sáng 08:00–12:00 và đổi chiều thành 13:00–15:00 thì ngày chuẩn là
**6 giờ**, nghỉ giữa giờ 12:00–13:00. Nếu chọn một ca chỉ gồm 13:00–15:00 thì
ngày chuẩn là **2 giờ**. Form hiển thị tổng thời lượng và khoảng nghỉ sau chỉnh
sửa để phân biệt hai lựa chọn này; không tiếp tục yêu cầu đủ 8 giờ cho ca đã đổi.

Luồng quản trị: tạo/chọn mẫu ca → chỉnh các khoảng giờ → chọn phạm vi → chọn
ngày bắt đầu, ngày kết thúc tùy chọn và các ngày làm việc áp dụng → xem trước
lịch cũ/mới, tổng giờ, nhân viên bị ảnh hưởng và xung đột → lưu áp dụng.

| Phạm vi           | Hành vi                                                                                                                                                                                       |
| ----------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Mặc định công ty  | Nhân viên không có phân công riêng kế thừa; nhân viên mới cũng kế thừa theo ngày hiệu lực và lịch làm việc                                                                                    |
| Một/vài nhân viên | Chọn danh sách nhân viên, tạo phân công riêng; không sửa mẫu đang áp dụng cho người khác                                                                                                      |
| Toàn bộ nhân viên | Áp dụng cho mọi nhân viên đủ điều kiện trong khoảng hiệu lực, đồng thời thay thế phân công riêng trùng phạm vi sau khi hiển thị trong bản xem trước; cập nhật mặc định cho người gia nhập sau |

Phân công riêng có ưu tiên cao hơn mặc định công ty. Hai phân công riêng cùng
nhân viên chồng ngày không được âm thầm chọn theo thời điểm tạo: thao tác thay
thế phải tách/kết thúc khoảng hiệu lực cũ và ghi lịch sử. Hết hạn phân công
riêng thì quay về mặc định có hiệu lực lúc đó. Áp dụng toàn bộ không bỏ sót
nhân viên đang có lịch riêng; nếu chỉ muốn đổi mặc định và giữ ngoại lệ thì
chọn rõ phạm vi "Mặc định công ty".

Mẫu ca đã được dùng phải tạo revision mới khi sửa. Bước lưu áp dụng cập nhật
phân công tương lai trong phạm vi đã chọn, giữ snapshot lịch cũ ngoài phạm vi.
Ngày đã có chấm công/đơn đã duyệt bị ảnh hưởng hoặc thuộc kỳ đã khóa phải qua
luồng đối soát/điều chỉnh tương ứng; không đổi giờ hoặc thời lượng phép ngầm.
Thao tác hàng loạt cần có batch ID, version và idempotency; trả kết quả rõ ràng,
không báo thành công toàn bộ khi còn nhân viên chưa áp dụng được.

Validation: ca trong ngày có các khoảng bắt đầu trước kết thúc và không chồng
lấn. Ca qua đêm đã hỗ trợ một khoảng với `overnight: true`, kết thúc ngày sau;
thời lượng chuẩn là tổng khoảng làm việc. Nhãn buổi phục vụ ánh xạ phép không
bị suy ra lại từ mốc 12:00.
Đổi chiều sang 13:00–15:00 thì nghỉ `AFTERNOON` loại đúng 120 phút khỏi công;
số dư phép vẫn theo đơn vị 0,5 ngày theo chính sách hiện tại. Ca một khoảng
phải có ánh xạ nghỉ nửa ngày rõ ràng trước khi nhận loại đơn đó (xem mục 4).

<a id="design-2-trách-nhiệm-service-và-dữ-liệu"></a>

### 2. Trách nhiệm service và dữ liệu

| Service    | Sở hữu                                                                                                                                       |
| ---------- | -------------------------------------------------------------------------------------------------------------------------------------------- |
| Employee   | Nhân viên, phòng ban, ngày bắt đầu/kết thúc làm việc                                                                                         |
| Calendar   | Ngày lễ/ngày nghỉ chung có hiệu lực                                                                                                          |
| Leave      | Đơn nghỉ cả ngày/nửa ngày, số dư phép, phê duyệt và lịch sử nghỉ phép                                                                        |
| Attendance | Mẫu ca, phân công, sự kiện vào/ra, đơn đi trễ/về sớm, điều chỉnh công và lịch sử duyệt, OT, công tạm tính và CSV; chốt/mở lại tháng và lịch sử phiên bản |

Chưa cần Shift Service hoặc Report Service riêng. Không đọc chéo database.
Attendance lấy dữ liệu qua API nội bộ có xác thực hoặc bản sao từ sự kiện;
ghi event/session/daily và kết quả idempotency trong transaction database cục bộ.

Mô hình dự kiến trong Attendance:

| Thành phần                                  | Trường/quy tắc chính                                                                                                |
| ------------------------------------------- | ------------------------------------------------------------------------------------------------------------------- |
| `attendance_policies`                       | `mode`, `timezone`, quy tắc nghỉ giữa giờ, quy tắc làm tròn, ngày hiệu lực, version                                 |
| `work_shifts`                               | Mẫu có tên, revision, các khoảng làm việc gắn nhãn buổi và giờ nghỉ cho `FIXED_SHIFT`                               |
| `work_schedule_rules`                       | Phạm vi công ty/danh sách nhân viên, ngày bắt đầu/kết thúc, ngày làm việc áp dụng, shift revision, lịch sử thay thế |
| `work_schedule_assignments`                 | Nhân viên, ngày công, policy/shift snapshot, thời lượng chuẩn, cửa sổ ghi nhận                                      |
| `attendance_events` / `attendance_sessions` | Giờ vào/ra gốc, nguồn, thời điểm server nhận, phiên hợp lệ                                                          |
| `attendance_corrections`                    | Bổ sung/điều chỉnh có người thực hiện, lý do, lịch sử                                                               |
| `attendance_daily`                          | Thời lượng thực tế, được tính công, phép, sai lệch, nguồn và phiên bản tính                                         |
| `attendance_periods`                        | Kỳ, trạng thái mở/khóa, revision báo cáo                                                                            |

`FIXED_SHIFT` lấy thời lượng chuẩn từ tổng các khoảng làm việc của ca; không
lưu một mục tiêu độc lập có thể mâu thuẫn với ca. `FLEXIBLE_DURATION` có
`target_minutes` (ví dụ 480 hoặc 150) và cửa sổ được ghi nhận trong ngày.
Cửa sổ này xác định thời gian hợp lệ, không phải mốc đi trễ/về sớm.

Snapshot phân công phải gồm cả policy, giờ nghỉ, mục tiêu, lịch nghỉ và phiên
bản nguồn. Sửa mẫu ca/chính sách chỉ ảnh hưởng các phân công tương lai; tính lại
quá khứ là thao tác tường minh có audit. Thiếu dữ liệu nguồn không mặc định là
không có ngày lễ/đơn phép.

<a id="design-3-giờ-thực-tế-nghỉ-giữa-giờ-và-ngày-công"></a>

### 3. Giờ thực tế, nghỉ giữa giờ và ngày công

- Lưu timestamp gốc; không sửa giờ vào/ra thành giờ đã làm tròn.
- Gán `work_date` theo lịch và timezone, hiện một ca/ngày và một cặp vào/ra.
  Ca qua đêm đã có và thuộc ngày bắt đầu ca; nhiều ca/phiên vẫn cần mở rộng.
- Thời lượng được chứng minh bởi cặp vào/ra hợp lệ, trừ giờ nghỉ không tính công.
  Với nghỉ trưa cố định, chỉ trừ phần giao thực tế với khoảng nghỉ; không trừ
  một giờ nghỉ trưa cho ca 10:00–12:00 nếu ca không giao với giờ nghỉ.
- Lịch linh hoạt vẫn cần chính sách nghỉ rõ ràng. Ví dụ vào 09:00, ra 18:00,
  nghỉ 12:00–13:00 thì làm 8 giờ, không phải 9 giờ.
- Khi hỗ trợ nhiều phiên, lấy hợp các khoảng hợp lệ và loại giờ nghỉ một lần;
  không tính khoảng ra ngoài giữa hai phiên là giờ làm. Nghỉ linh hoạt cần
  dữ liệu break/phiên tương ứng, không suy ra từ lần vào đầu và ra cuối.
- Thiếu check-in/check-out: `INCOMPLETE`, chưa có thời lượng cuối cùng. Không
  có event sau hạn: `NO_RECORD`, cần đối chiếu phép trước khi kết luận vắng.
  Ngày nghỉ toàn bộ có đơn hợp lệ không đòi cặp vào/ra cho phần đã nghỉ.

<a id="design-4-kết-hợp-nghỉ-phép-theo-ngày"></a>

### 4. Kết hợp nghỉ phép theo ngày

Leave giữ đơn vị **0,5 ngày** cho `ANNUAL`/`UNPAID`; chỉ `ANNUAL` trừ số dư phép
năm. Phút quy đổi phục vụ đối soát công, không thay đơn vị số dư và không tự
biến thiếu giờ thành đơn nghỉ phép.

- `FIXED_SHIFT`: nghỉ buổi sáng/chiều loại đúng khoảng tương ứng khỏi thời gian
  phải làm. Bản phân công cần ánh xạ buổi theo lịch, không hard-code nửa ngày
  bằng 4 giờ. Ví dụ nghỉ sáng thì xét đi trễ từ đầu ca chiều.
- `FLEXIBLE_DURATION`: đề xuất nghỉ 0,5 ngày giảm 50% mục tiêu ngày, nghỉ 1 ngày
  giảm toàn bộ mục tiêu. Mục tiêu 480 phút, nghỉ 0,5 ngày thì còn phải làm 240
  phút. Nếu không ràng buộc buổi nghỉ, UI/API tương lai cần lựa chọn `HALF_DAY`
  trung tính; giá trị này **chưa được API Leave hiện tại hỗ trợ**. Không âm thầm
  đổi nghĩa `MORNING`/`AFTERNOON` trong đơn cũ.
- Part-time: cần cấu hình quyền hưởng phép và ánh xạ nghỉ trước khi cho gửi đơn.
  Đề xuất nghỉ toàn bộ ngày được phân công là 1 ngày theo lịch cá nhân, nửa ngày
  là một nửa lịch/mục tiêu cá nhân; đây là chính sách cần chốt trước khi bật,
  không tự áp 12 ngày/năm hoặc coi ca 2 giờ là 0,5 ngày của lịch full-time.
- Ngày lễ/ngày không làm việc không trừ phép. Leave hiện còn tính theo ngày
  lịch nên phải nâng cấp cách tính và lưu snapshot trước khi tích hợp chính thức.
- Chỉ đơn `APPROVED` có hiệu lực; đơn chờ duyệt được hiển thị để HR đối soát.
  Khoảng nghỉ không được tính hai lần. Đi làm trùng khoảng phép cố định phải
  báo xung đột để xử lý; không tự hoàn số dư hoặc cộng hai lần vào công.

Ký hiệu dùng trong công thức: `B` là thời lượng chuẩn của ngày sau lịch nghỉ
chung, `L` là thời lượng nghỉ phép đã duyệt quy đổi, `R = max(0, B - L)` là phần
còn phải làm. Với fixed, `L` lấy từ phần giao với lịch; với flex lấy từ hạn mức
được duyệt theo chính sách. Luôn bảo đảm `0 <= L <= B`.

<a id="design-5-thuật-toán-theo-chế-độ"></a>

### 5. Thuật toán theo chế độ

<a id="design-51-fixed_shift"></a>

#### 5.1. `FIXED_SHIFT`

Xét giờ vào/ra so với phần ca phải làm sau khi loại phép và giờ nghỉ. Lưu riêng
đi trễ/về sớm thực tế và phần được quy đổi. Quy tắc đã chốt:

```text
round15(duration_seconds) = ceil(max(0, duration_seconds) / 900) * 15
```

0 phút → 0; 5 phút → 15; 15 phút → 15; 16 phút → 30; 31 phút → 45.
Không cắt bỏ giây trước khi tính: 15 phút 1 giây → 30 phút. Không có khoảng
miễn trễ mặc định; bước 15 phút không phải grace period.

Làm tròn riêng đi trễ và về sớm từng ca, rồi cộng ngày/tháng. Với một phiên
hợp lệ, phần phải làm chỉ thiếu ở đầu/cuối và chưa có chính sách bù công:

```text
work_minutes_counted = max(0, R - round15(late_seconds) - round15(early_seconds))
```

Phần thiếu đầu/cuối chỉ đếm phút phải làm, không tính giờ nghỉ/phép và không
đếm chồng nhau. Trừ vào `R`, không trừ lại từ giờ thực tế (tránh trừ hai lần).
Ca có dữ liệu không đầy đủ hoặc các khoảng vắng giữa giờ không được áp công
thức giản lược này. Khi mở rộng nhiều phiên, phải tính thêm các khoảng vắng
và chốt quy tắc quy đổi tương ứng trước khi đưa vào sử dụng.

Đi trễ rồi ở lại ngoài ca không tự bù. Lưu riêng thời gian ngoài lịch; không
tự công nhận overtime hoặc cộng vào công chuẩn.

<a id="design-52-flexible_duration"></a>

#### 5.2. `FLEXIBLE_DURATION`

`W` là thời lượng làm hợp lệ trong cửa sổ ngày, sau khi loại giờ nghỉ. Không
tính đi trễ/về sớm; các trường đó là `null`/không áp dụng, không gán 0 để giả
định nhân viên đã tuân thủ một ca cố định.

```text
shortfall_actual = max(0, R - W)
work_minutes_counted = min(R, W)
extra_minutes = max(0, W - R)
```

Đủ `R` thì hoàn thành yêu cầu, bất kể bắt đầu sớm hay muộn trong cửa sổ hợp lệ.
Thiếu thì ghi `SHORT_HOURS`. Không tự bù thiếu hôm nay bằng giờ dư hôm khác.
Phần dư được lưu riêng; chưa phải overtime đã duyệt.

**Đề xuất mặc định:** thiếu thời lượng linh hoạt giữ đúng số phút/giây thực tế,
`shortfall_rounding=NONE`. Quy tắc 15 phút đã chốt cho đi trễ/về sớm không tự
áp sang thiếu thời lượng. Nếu công ty chọn `CEIL_15`, làm tròn phần thiếu một
lần/ngày và tính `max(0, R - rounded_shortfall)`; không làm tròn giờ vào/ra hoặc
làm tròn riêng từng phiên. Lựa chọn này phải được lưu trong policy snapshot.

<a id="design-6-đơn-đi-trễvề-sớm-và-bù-công"></a>

### 6. Đơn đi trễ/về sớm và bù công

Attendance lưu `attendance_requests` và `attendance_request_history`: nhân viên,
ngày công, snapshot ca, loại `LATE_ARRIVAL`/`EARLY_DEPARTURE`, buổi, giờ dự kiến,
lý do, trạng thái, version và lịch sử xét duyệt. API `/api/v1/attendance/requests`;
UI `/attendance/requests`. Giờ dự kiến chính xác đến phút và nằm trong khoảng
làm việc; backend tự tính số phút xin phép. Quy tắc làm tròn 15 phút áp dụng khi
tính sai lệch chấm công, không bắt buộc số phút xin phép là bội số của 15.

Chỉ áp dụng loại đơn này cho `FIXED_SHIFT`. Với flex thuần túy không có mốc trễ/
sớm, giao diện không yêu cầu đơn đó. Nếu tương lai có xin miễn một phần thời
lượng ngày, cần nghiệp vụ riêng; không dùng nhãn đi trễ cho thiếu giờ linh hoạt.

HR/ADMIN duyệt, cấm tự duyệt, dùng `If-Match`, audit và khóa điều phối trong
transaction Attendance để kiểm tra lịch và đơn khác. Attendance kiểm tra phép
qua REST của Leave; không có transaction chung giữa hai service. Phân công và
snapshot được xác minh trong Attendance, không tin giờ ca hoặc thời lượng client gửi.

Phê duyệt xác nhận khoảng vắng được cho phép, không tự đổi timestamp, trừ phép
năm hoặc bù đủ công. Đề xuất mặc định `permission_credit=NONE`; bù công có
lương cần chính sách riêng được cấu hình trước khi bật. Khi đối soát, lấy giao
của khoảng vắng thực tế với khoảng đã duyệt để xác định phần có phép. Giữ các
phần này ở độ chính xác thực tế; làm tròn tổng đi trễ/về sớm một lần theo mục 5,
không làm tròn từng phần có phép/chưa có phép rồi cộng gây tăng mức khấu trừ.

<a id="design-7-ví-dụ-đối-chiếu"></a>

### 7. Ví dụ đối chiếu

Các ví dụ fixed không có bù công; flex dùng `shortfall_rounding=NONE`. Cặp giờ
trong bảng đủ dữ liệu; mọi giờ nghỉ đều ghi rõ.

| Lịch                              | Nghỉ phép                 | Ghi nhận                       | Giờ làm thực tế hợp lệ | Giờ làm tính công | Kết quả khác                                       |
| --------------------------------- | ------------------------- | ------------------------------ | ---------------------- | ----------------- | -------------------------------------------------- |
| Fixed mặc định 08–12, 13:30–17:30 | Nghỉ sáng                 | 13:38–17:30                    | 3h52                   | 3h45              | Phép 0,5 ngày; trễ thực tế 8p, quy đổi 15p         |
| Fixed đã đổi 08–12, 13–15         | Nghỉ sáng                 | 13:08–15:00                    | 1h52                   | 1h45              | Ngày chuẩn 6h; phép sáng quy đổi 4h                |
| Fixed chỉ có 13–15                | Không                     | 13:00–15:00                    | 2h                     | 2h                | Ngày chuẩn 2h, không thiếu 6h                      |
| Fixed 10–12                       | Không                     | 10:08–12:00                    | 1h52                   | 1h45              | Trễ quy đổi 15p                                    |
| Fixed 13–15:30                    | Không                     | 13:08–15:30                    | 2h22                   | 2h15              | Trễ quy đổi 15p                                    |
| Fixed mặc định 08–12, 13:30–17:30 | Không                     | 08:08–17:38, nghỉ 12–13:30     | 7h52 trong ca          | 7h45              | Ngoài ca 8p, không bù trễ                          |
| Flex 8h, cửa sổ 06–22             | Không                     | 09:08–18:08, nghỉ 12–13        | 8h                     | 8h                | Đủ thời lượng, không có đi trễ                     |
| Flex 8h, cửa sổ 06–22             | Không                     | 09:08–18:00, nghỉ 12–13        | 7h52                   | 7h52              | Thiếu 8p; nếu policy chọn CEIL_15 thì công là 7h45 |
| Flex 8h, cửa sổ 06–22             | Nghỉ 0,5 ngày, quy đổi 4h | 13:08–17:08                    | 4h                     | 4h                | Đủ mục tiêu còn lại; phép ghi riêng                |
| Flex part-time 2h30, cửa sổ 06–22 | Không                     | 14:00–16:30, không có giờ nghỉ | 2h30                   | 2h30              | Đủ thời lượng                                      |

<a id="design-8-tổng-hợp-chốt-công-và-csv"></a>

### 8. Tổng hợp, chốt công và CSV

Kết quả chung cho cả hai chế độ, một dòng/nhân viên/ngày:

- Nhân viên, mã/phòng ban tại kỳ, ngày công, mode, assignment/policy version.
- `base_required_minutes`, `leave_days`, `annual_leave_minutes`,
  `unpaid_leave_minutes`, `remaining_required_minutes`.
- Giờ vào/ra, `worked_actual_minutes`, `work_minutes_counted`.
- Fixed: phút trễ/sớm thực tế, phút trễ/sớm quy đổi, khoảng vắng có phép và
  request IDs. Flex: `shortfall_actual_minutes`, `shortfall_counted_minutes`.
- Phút ngoài lịch/phần dư, trạng thái dữ liệu, đơn đang chờ, nguồn/phép đã áp
  dụng, calculation revision và trạng thái kỳ.

Không đồng nhất giờ làm tính công với công hưởng lương. Phép năm hưởng lương
được ghi riêng; nghỉ không lương không cộng vào công hưởng lương. Nếu công ty
cấu hình phép năm được cộng vào chỉ tiêu công hưởng lương thì cộng phần phép
đó với công làm việc, tối đa thời lượng chuẩn ngày cho phần công thông thường.
Không cộng phần dư thành overtime tự động. Nghỉ flex giảm mục tiêu nhưng làm
vượt mục tiêu còn lại phải hiện phần dư để đối soát, không cộng phép và giờ
dư hai lần, cũng không tự hoàn phép.

Màn hình và CSV đọc cùng revision tổng hợp. CSV chi tiết giữ cột mode và giá trị
không áp dụng để phân biệt thiếu giờ với đi trễ. Tổng tháng cộng từ ngày đã tính,
không làm tròn lại hoặc quy đổi mọi nhân viên sang ngày chuẩn 8h. Xuất UTF-8 BOM,
escape CSV và vô hiệu hóa ô có nguy cơ bị hiểu là công thức; không xuất lý do
riêng tư mặc định. Phân quyền xem/export thực thi ở backend.

Luồng đối soát: tính tạm → xử lý thiếu dữ liệu/đơn chờ → xác minh nguồn đã đồng
bộ → khóa kỳ → xuất bản chính thức. Báo cáo trước khóa ghi rõ tạm tính. Duyệt
đơn/correction đến muộn vào kỳ khóa phải báo chênh lệch, không ghi đè; mở lại
có quyền, lý do và audit rồi tạo revision mới. Khi Leave phát sự kiện, dùng
outbox cùng transaction duyệt và consumer chống trùng/sai thứ tự theo version.

<a id="design-9-trình-tự-triển-khai-và-kiểm-chứng"></a>

### 9. Trình tự triển khai và kiểm chứng

1. Tạo Attendance với policy/phân công/snapshot; HR/Admin quản lý `FIXED_SHIFT`
   mặc định 08–12, 13:30–17:30 và ca tùy chỉnh, áp dụng toàn bộ hoặc nhân viên
   được chọn. Ghi nhận vào/ra và tổng hợp có kiểm soát dữ liệu thiếu.
2. Đơn đi trễ/về sớm đã triển khai trong Attendance, không thuộc Leave.
   Tiếp tục hoàn thiện tính phép theo lịch/ngày nghỉ và đối soát dữ liệu đã duyệt.
3. Đối soát, khóa kỳ, CSV chi tiết và tổng tháng.
4. Triển khai `FLEXIBLE_DURATION` như tính năng nâng cao mặc định ẩn, sau khi
   hoàn thiện tính công, UI/validation, chính sách nghỉ/nửa ngày flex và kiểm thử.
   Thiết kế cơ chế bật tính năng và quyền truy cập ở giai đoạn đó. Chọn
   chế độ theo assignment, không tạo Attendance Service riêng cho từng chế độ.

Các ca nghiệm thu bắt buộc: toàn bộ ví dụ mục 7; 0/15/15 phút 1 giây ở biên
làm tròn; trễ 5p + sớm 5p → 30p; ca ngắn không cho công âm; nghỉ trưa giao một
phần; phép cả ngày không có event; thiếu check-out; ngày lễ không trừ phép;
flex làm dư không tự tính overtime; đơn trùng/duyệt đồng thời; event trùng/sai
thứ tự; đổi policy không đổi công đã khóa; CSV khớp revision trên màn hình.
Kiểm tra thêm quyền quản lý ca; lịch mặc định 480 phút; đổi riêng buổi chiều
thành 13–15 còn 360 phút/ngày; ca chỉ 13–15 là 120 phút; phân công vài nhân viên
không ảnh hưởng người khác; áp dụng toàn bộ xử lý cả ngoại lệ; hết hạn lịch riêng
quay về mặc định; ngày hiệu lực không làm đổi snapshot hoặc số dư phép quá khứ.

Đây là tiêu chí cho implementation tương lai, chưa phải bộ test đã chạy.
