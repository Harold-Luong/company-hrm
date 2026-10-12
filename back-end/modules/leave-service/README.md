# Leave Service

**Mục lục**

- [Cấu hình và chạy độc lập](#cấu-hình-và-chạy-độc-lập)
- [Phạm vi và quy tắc](#phạm-vi-và-quy-tắc)
- [Thiết kế nghỉ phép, lịch làm việc và tích hợp công](#thiết-kế-nghỉ-phép-lịch-làm-việc-và-tích-hợp-công)
- [Chạy local](#chạy-local)
- [API](#api)
- [Giao diện và kiểm thử](#giao-diện-và-kiểm-thử)
- [Coverage cho Attendance](#coverage-cho-attendance)

Ứng dụng Spring Boot độc lập tại `:8084`, sở hữu PostgreSQL `leave_db`. Nhân viên
gửi đơn nghỉ cả ngày hoặc nửa ngày; đơn xuất hiện trong hàng chờ chung của HR/ADMIN. Nhân viên
xem kết quả trên hệ thống, chưa có email/push hoặc phân công một HR cụ thể.

Chạy native với `.env` theo [hướng dẫn chạy độc lập](../../../README.md#local). Các ví dụ database bên dưới dùng mặc định trong mã nguồn (`5432`); mẫu `.env` hiện dùng `55432` và khóa trong `.cache/local/keys`. Nạp file vào environment trước khi chạy Maven, hoặc chỉnh biến theo database/khóa của máy.

## Cấu hình và chạy độc lập

Project Maven riêng, Java 21, cổng mặc định 8084. Build/chạy từ thư mục
leave-service; không cần chạy Maven ở gốc repository hoặc build service khác.

Điều kiện: PostgreSQL/schema và access public key. `HRM_NOTIFICATIONS_ENABLED=false` cho phép dùng nghiệp vụ nghỉ phép mà không cần Kafka/Notification.

1. Chuẩn bị database/user riêng và SQL theo phần database trong `README` này.
2. Tạo `.env` từ [.env.example](.env.example) nếu chưa có, rồi sửa host/port,
   mật khẩu, đường dẫn khóa và các cờ tính năng theo môi trường thực tế.
3. Nạp biến vào đúng terminal hoặc Run Configuration của IDE trước khi chạy.

Các lệnh sau dùng Git Bash/Bash, bắt đầu từ gốc repository:

```bash
cd back-end/modules/leave-service
test -f .env || cp .env.example .env
# Sửa và lưu .env trước khi chạy các lệnh tiếp theo.
set -a
source ./.env
set +a
mvn spring-boot:run
```

Maven/Spring Boot không tự đọc .env. IDE cần working directory là project và
biến môi trường riêng; thay `.env` phải nạp lại và restart project đó. Mẫu hiện
trỏ tới cache local đã có; máy mới phải đổi đường dẫn, không cần tạo cache chung.

| Biến môi trường | Cấu hình cho project này |
| --- | --- |
| `SERVER_PORT` | 8084 |
| `SPRING_DATASOURCE_URL` | jdbc:postgresql://localhost:5432/leave_db; sửa port nếu DB dùng 55432 hoặc port khác |
| `SPRING_DATASOURCE_USERNAME` | leave_user |
| `SPRING_DATASOURCE_PASSWORD` | Mật khẩu của user database này |
| `JWT_ACCESS_PUBLIC_KEY` | file:/duong-dan-tuyet-doi/access-public.pem |
| `JWT_ACCESS_ISSUER` / `JWT_ACCESS_AUDIENCE` | auth-service / hrm-api-access, khớp Auth |

Chỉ cần access public key của Auth; không cấp private key cho project này.

`SPRING_DATASOURCE_*` ưu tiên hơn biến `<SERVICE>_DB_*`; `SERVER_PORT` ưu tiên hơn
biến cổng riêng nếu có. Không giữ hai bộ giá trị mâu thuẫn trong environment.
Dừng bằng `Ctrl+C` trong terminal này. Build riêng bằng `mvn package`; kiểm thử theo
phần kiểm thử bên dưới. Với backend có wrapper có thể dùng `./mvnw` hoặc `mvnw.cmd`
thay mvn. Không bắt buộc `run-all.sh` hoặc Docker Compose để chạy project.

## Phạm vi và quy tắc

- Danh tính người gửi lấy từ `sub` và `employee_id` trong access JWT của Auth;
  request body không quyết định người gửi, trạng thái hay người duyệt.
- Loại nghỉ: `ANNUAL` (phép năm), `UNPAID` (không lương). Phép `ANNUAL` được cấp tự động
  12 ngày (24 đơn vị nửa ngày) cho mỗi nhân viên trong mỗi năm dương lịch.
- Đơn vị nghỉ phép nhỏ nhất là **nửa ngày**: `MORNING`/`AFTERNOON` dùng 1 unit,
  `FULL_DAY` dùng 2 units/ngày. Không nhận đơn nghỉ phép theo giờ hoặc phút.
- Lý do nghỉ là trường `reason` riêng, bắt buộc, tối đa 2.000 ký tự. Có thể ghi
  "Việc cá nhân", "Việc gia đình", "Khám bệnh"; không yêu cầu chi tiết bệnh lý.
  `SICK`/`OTHER` không còn là loại nghỉ hợp lệ trong API.
- Ngày bắt đầu từ hôm nay theo `Asia/Ho_Chi_Minh`; ngày kết thúc được tính gồm
  cả ngày đó. Tối đa 366 ngày lịch/đơn. `FULL_DAY` tính 2 units mỗi ngày lịch;
  `MORNING`/`AFTERNOON` tính 1 unit và chỉ áp dụng cho đơn một ngày. Chưa loại
  cuối tuần/ngày lễ và chưa hỗ trợ theo giờ hoặc gửi bù đơn quá khứ.
- Đơn `ANNUAL` không được đi qua hai năm. Số dư được tạo lười khi đọc hoặc duyệt;
  chỉ trừ khi HR/ADMIN duyệt. Nếu không đủ số dư, thao tác duyệt trả `409`.
- `PENDING → APPROVED / REJECTED / CANCELLED`. Người gửi chỉ rút đơn đang chờ;
  đơn đã duyệt có luồng yêu cầu điều chỉnh riêng bên dưới. Đơn từ chối/rút không sửa được. Từ chối cần lý do; ghi chú duyệt là tùy chọn.
- Nhân viên chỉ xem đơn/lịch sử của mình. HR/ADMIN xem hàng chờ toàn công ty,
  nhưng không được duyệt hoặc từ chối đơn của chính mình. MANAGER chưa có quyền duyệt.
- Chặn khoảng thời gian trùng với đơn `PENDING`/`APPROVED` của cùng nhân viên, kể cả
  gửi đồng thời. Khóa hàng `leave_request_owners` trong transaction để tuần tự hóa
  kiểm tra và tạo đơn theo nhân viên. Hai đơn `MORNING` và `AFTERNOON` được phép
  cùng ngày; đơn bị từ chối/rút không chặn gửi lại.
- Duyệt/từ chối/rút dùng `If-Match: "<version>"`. Cập nhật có điều kiện theo
  version và trạng thái, chỉ một thao tác đồng thời thắng. Ghi lịch sử cùng
  transaction; lỗi ghi lịch sử sẽ rollback thay đổi.
- Có outbox Kafka cho thông báo gửi/duyệt/từ chối/rút đơn khi bật `HRM_NOTIFICATIONS_ENABLED`.
  Xem [Notification](../notification-service/README.md) để áp SQL `004` và chuẩn bị topic/consumer.
  Coverage Attendance vẫn đọc qua REST, chưa tự làm mới khi phép thay đổi. Chỉ `ANNUAL`
  dùng sổ số dư; `UNPAID` không trừ số dư phép năm.

## Thiết kế nghỉ phép, lịch làm việc và tích hợp công

Thiết kế chung được tổng hợp tại [Attendance Design](../attendance-service/README.md#design),
bao gồm các ví dụ tính công, quyền sở hữu dữ liệu, đối soát và xuất CSV.
Attendance đã có [API/UI ca cố định, phân công, chấm công và CSV tạm tính](../attendance-service/README.md),
đọc phép qua endpoint coverage. Đơn đi trễ/về sớm đã có tại Attendance; lịch linh hoạt
và tự đồng bộ ngày công khi phép thay đổi chưa được triển khai.

- Leave quản lý phép theo ngày, bước **0,5 ngày**, và phê duyệt; đơn đi trễ/về sớm đã có tại Attendance; Attendance quản lý lịch/ca, giờ thực tế và kết quả tính công.
- `FIXED_SHIFT`: làm theo giờ ca; đi trễ/về sớm làm tròn lên riêng từng loại,
  bước **15 phút** (5 phút → 15; 16 phút → 30). Giữ giờ thực tế để đối soát.
- HR/Admin tạo và chỉnh ca, mặc định **08:00–12:00, 13:30–17:30**. Có thể đổi
  khoảng giờ (ví dụ chiều 13:00–15:00), áp dụng toàn bộ hoặc một/vài nhân viên
  theo ngày hiệu lực. Tổng giờ tính từ lịch đã gán; xem thiết kế chung về
  ưu tiên lịch riêng và xử lý ngoại lệ khi áp dụng toàn bộ.
- `FLEXIBLE_DURATION`: chỉ yêu cầu đủ thời lượng ngày, ví dụ 8 giờ; không đánh
  giá đi trễ/về sớm. Đề xuất thiếu giờ giữ thời lượng thực tế mặc định; chỉ làm
  tròn thiếu giờ lên 15 phút nếu công ty cấu hình chính sách riêng.
- Part-time có thể dùng một trong hai chế độ: ca cố định 10:00–12:00 hoặc
  13:00–15:30; hoặc lịch linh hoạt yêu cầu đủ 2/2,5 giờ. Không hard-code ngày 8h.
- Với lịch cố định, phép loại đúng khoảng buổi được duyệt. Với flex, đề xuất
  nghỉ 0,5 ngày giảm nửa mục tiêu ngày; UI/API nửa ngày không gắn buổi và chính
  sách phép part-time phải hoàn thiện trước khi bật, chưa phải khả năng API hiện tại.
- Ví dụ nghỉ phép sáng, chiều phải làm 13:30–17:30, vào 13:38 và ra 17:30:
  ghi riêng phép 0,5 ngày, làm thực tế 3h52, làm việc tính công 3h45.
- Bước 15 phút không tự trừ số dư phép hoặc quyết định bù công/hưởng lương.
  Giờ làm thực tế, giờ làm tính công và thời lượng nghỉ phép được lưu riêng.
- Trước khi tích hợp chính thức phải sửa tính phép theo lịch làm việc/ngày lễ,
  lưu snapshot, xử lý đồng bộ, chốt kỳ và revision báo cáo theo thiết kế chung.

## Điều chỉnh phép đã duyệt

Nhân viên mở chi tiết đơn đã duyệt, chọn hủy toàn bộ hoặc giảm thời gian nghỉ và nhập lý do. Khi giảm, chọn **khoảng thời gian vẫn nghỉ**: một khoảng liên tục nằm trong đơn gốc, hỗ trợ nửa ngày. Có thể đối soát đơn của ngày đã qua. Không hỗ trợ tăng ngày nghỉ hoặc tách thành nhiều khoảng trong cùng yêu cầu.

Trong lúc chờ HR/ADMIN khác xác nhận, đơn gốc, lịch cá nhân và số dư giữ nguyên. Duyệt hủy chuyển đơn sang `CANCELLED`; duyệt giảm giữ `APPROVED` với khoảng ngày mới. Phép năm hoàn đúng phần chênh lệch vào năm của đơn gốc; nghỉ không lương không thay đổi số dư. Từ chối hoặc rút yêu cầu không hoàn phép. Mỗi đơn chỉ có một yêu cầu điều chỉnh đang chờ; không được tự duyệt, kể cả HR/ADMIN.

Các API dưới `/api/v1/leave/requests`:

| API | Quyền / nội dung |
| --- | --- |
| `GET /{id}/adjustments` | Chủ đơn hoặc HR/ADMIN; lịch sử gồm thời gian gốc và đề xuất |
| `POST /{id}/adjustments` | Chủ đơn; `{cancelAll, startDate, endDate, period, reason}`; hủy toàn bộ gửi ngày và period là null |
| `PATCH /{id}/adjustments/{adjustmentId}/approve` | HR/ADMIN khác; body `{note}` tùy chọn |
| `PATCH /{id}/adjustments/{adjustmentId}/reject` | HR/ADMIN khác; bắt buộc `{note}` |
| `PATCH /{id}/adjustments/{adjustmentId}/cancel` | Chủ đơn rút yêu cầu |
| `GET /adjustments/inbox?page=0&size=20` | HR/ADMIN; các đơn đang chờ điều chỉnh |

Mọi thao tác ghi yêu cầu `If-Match` theo **version đơn gốc** và trả ETag mới. Duyệt, hoàn phép, ghi lịch sử và outbox nằm trong một transaction, khóa theo nhân viên; thao tác lặp hoặc dùng version cũ không hoàn phép lần nữa. `pending-count` bao gồm cả yêu cầu điều chỉnh. Outbox dùng action `UPDATED` để làm mới badge/lịch qua SSE hiện có; vẫn có polling dự phòng 30 giây.

Coverage nghỉ phép trả khoảng ngày mới sau khi duyệt. Bảng công đã lưu snapshot cần HR gọi thao tác `refresh-coverage` hiện có để đối soát; không tự thay đổi snapshot cũ. Xin đi trễ nhưng đến đúng giờ vẫn được tính theo giờ chấm công thực tế, không phát sinh số phút đi trễ chỉ vì có đơn đã duyệt.

## Chạy local

Yêu cầu JDK 21, PostgreSQL và access public key đúng với Auth. Tạo database/user
bằng tài khoản quản trị PostgreSQL (đặt mật khẩu riêng nếu dùng ngoài local):

```sql
CREATE USER leave_user WITH PASSWORD 'leave_password';
CREATE DATABASE leave_db OWNER leave_user;
```

Từ thư mục `back-end/modules/leave-service`, khởi tạo schema một lần trên database mới:

```bash
psql -h localhost -U leave_user -d leave_db -v ON_ERROR_STOP=1 -f docs/sql/001_leave_schema.sql
psql -h localhost -U leave_user -d leave_db -v ON_ERROR_STOP=1 -f docs/sql/005_leave_adjustments.sql
./mvnw spring-boot:run
```

Database mới chạy `001_leave_schema.sql` và `005_leave_adjustments.sql`; database hiện có phải áp `005_leave_adjustments.sql` trước khi chạy bản mới. Nếu bật thông báo, áp thêm `004_notification_outbox.sql`. Database đã có tính năng
nửa ngày/số dư nhưng còn constraint bốn loại nghỉ cần dừng service cũ và chạy
`docs/sql/003_annual_unpaid_types.sql` trước khi triển khai bản API mới.
Script này chỉ thu hẹp constraint, không tự đổi loại đơn hay số dư. Nếu còn đơn
`SICK`/`OTHER`, script dừng và rollback: HR cần xác nhận phân loại từng đơn,
bảo toàn lý do/lịch sử và đối soát số dư của đơn đã duyệt trước khi nâng cấp.
Có thể kiểm tra bằng `SELECT id, employee_id, leave_type, status FROM leave_requests
WHERE leave_type NOT IN ('ANNUAL', 'UNPAID');`.

Hoặc chạy `./run-all.sh` tại gốc repo sau khi đã chuẩn bị tất cả database.
Không tự chạy schema/seed hoặc nâng cấp database khi ứng dụng khởi động.

| Biến | Mặc định local |
| --- | --- |
| `LEAVE_PORT` | `8084` |
| `LEAVE_DB_URL` | `jdbc:postgresql://localhost:5432/leave_db` |
| `LEAVE_DB_USER` | `leave_user` |
| `LEAVE_DB_PASSWORD` | `leave_password` |
| `JWT_ACCESS_PUBLIC_KEY` | `file:../auth-service-main/keys/access-public.pem` |
| `JWT_ACCESS_ISSUER` | `auth-service` |
| `JWT_ACCESS_AUDIENCE` | `hrm-api-access` |

Spring Boot không tự đọc `.env`. Export các biến trên hoặc cấu hình trong IDE.
Gateway mặc định dùng `LEAVE_SERVICE_URL=http://localhost:8084`. Compose tại
`../infra/gateway` có Leave và database riêng; cần thêm `LEAVE_DB_PASSWORD` vào
file `.env` triển khai. Database mới tự khởi tạo schema khi volume còn trống.

## API

Prefix `/api/v1/leave/requests`, tất cả cần Bearer access JWT. JWT RS256 được
xác minh cục bộ; Logout/khóa tài khoản không tự thu hồi access token đã phát hành.

| Method / đường dẫn | Quyền / nội dung |
| --- | --- |
| `POST /` | Người đã đăng nhập tạo đơn cho chính mình; trả `201`, `Location`, `ETag` |
| `GET /mine?status=&page=0&size=20` | Đơn của mình; status tùy chọn |
| `GET /calendar?year=2030` | Nghỉ phép cá nhân giao với năm đã chọn, chỉ `PENDING`/`APPROVED`; mọi role chỉ nhận dữ liệu theo `employee_id` trong JWT |
| `GET /balance?year=2030` | Số dư phép năm của chính mình; mặc định là năm hiện tại |
| `GET /pending-count` | HR/ADMIN; tổng số đơn `PENDING` toàn công ty, gồm cả đơn của người gọi; dùng cho badge hàng chờ trên UI |
| `GET /inbox?status=PENDING&page=0&size=20` | HR/ADMIN; bỏ status để xem mọi trạng thái |
| `GET /{id}` | Người gửi hoặc HR/ADMIN, trả `ETag` |
| `GET /{id}/history` | Người gửi hoặc HR/ADMIN |
| `PATCH /{id}/approve` | HR/ADMIN khác người gửi, body `{"note":"Đồng ý"}` hoặc `{}` |
| `PATCH /{id}/reject` | HR/ADMIN khác người gửi, body `{"note":"Lý do từ chối"}` |
| `PATCH /{id}/cancel` | Người gửi rút đơn đang chờ; không cần body |

POST gửi tới đúng `/api/v1/leave/requests` (không cần slash cuối):

```json
{
  "leaveType": "ANNUAL",
  "startDate": "2030-02-01",
  "endDate": "2030-02-03",
  "period": "FULL_DAY",
  "reason": "Việc gia đình"
}
```

`period` nhận `FULL_DAY`, `MORNING`, `AFTERNOON` và mặc định `FULL_DAY` để tương
thích client cũ. Với nghỉ nửa ngày, `startDate` phải bằng `endDate`.

Các PATCH bắt buộc `If-Match: "0"` (dùng version hiện tại). Lỗi theo Problem
Detail: `400` dữ liệu sai; `401/403` xác thực/quyền; `404` không tồn tại hoặc
không thuộc phạm vi nhân viên; `409` trùng ngày/trạng thái đã kết thúc;
`412` version cũ; `428` thiếu If-Match; `503` database chưa sẵn sàng.
Nếu timeout khi gửi, tải lại danh sách trước khi gửi lại; POST chưa có replay
theo Idempotency-Key, đơn đang chờ/đã duyệt trùng ngày sẽ bị chặn.

Danh sách phân trang 0-based, size 1–100, sắp xếp mới nhất trước; response gồm
`content`, `page`, `size`, `totalElements`, `totalPages`. HR UI lấy tên/mã nhân viên
qua Employee API; nếu Employee không sẵn sàng vẫn hiển thị UUID và xử lý đơn.
Lý do nghỉ và lịch sử không được công bố sang lịch chung.

Health trực tiếp: `GET :8084/actuator/health`; qua Gateway:
`GET /api/v1/leave/health-check` (cần access token).

## Giao diện và kiểm thử

- `/leave`: form gửi đơn, danh sách cá nhân, trạng thái.
- `/leave/inbox`: hàng chờ/lịch sử các đơn cho HR/ADMIN, lọc trạng thái.
- `/leave/requests/:id`: chi tiết, phản hồi, lịch sử và thao tác theo quyền.

`./mvnw test` chạy kiểm thử JWT/quyền mà không cần database; bộ integration được
bỏ qua nếu chưa đặt `LEAVE_TEST_DB_URL`. Bộ integration **xóa dữ liệu các bảng
Leave trong database test trước mỗi test**, chỉ trỏ tới database dùng riêng:

```bash
# Tạo database test riêng và áp dụng docs/sql/001_leave_schema.sql, docs/sql/005_leave_adjustments.sql trước.
LEAVE_TEST_DB_URL=jdbc:postgresql://localhost:5432/leave_test \
LEAVE_TEST_DB_USER=leave_test LEAVE_TEST_DB_PASSWORD=your-test-password ./mvnw test
```

Kiểm thử gồm workflow, quyền sở hữu, tự duyệt, version, xử lý đồng thời,
chống trùng khoảng ngày và rollback audit. Frontend có unit test và Playwright:
`npm run test`, `npx playwright test tests/e2e/leave.spec.js` trong `frontend`.

## Coverage cho Attendance

`GET /api/v1/leave/requests/attendance?from=YYYY-MM-DD&until=YYYY-MM-DD&employeeId=UUID`
trả các đơn PENDING/APPROVED giao khoảng ngày, gồm id, employeeId, loại, buổi,
ngày và version. Không trả reason/note. Nhân viên chỉ đọc bản thân; HR/ADMIN có thể
chọn nhân viên hoặc toàn công ty. Tối đa 2.000 đơn; vượt giới hạn trả 422, không cắt
mất dữ liệu. Attendance dùng APPROVED tính thời lượng phép theo ca và PENDING làm
cờ đối soát. Endpoint này không thay đổi quy tắc sổ số dư Leave, không tự đồng bộ
ngày công đã snapshot; HR cập nhật coverage qua Attendance khi nguồn thay đổi.
