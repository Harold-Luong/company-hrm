# Leave Service

Ứng dụng Spring Boot độc lập tại `:8084`, sở hữu PostgreSQL `leave_db`. Nhân viên
gửi đơn nghỉ cả ngày hoặc nửa ngày; đơn xuất hiện trong hàng chờ chung của HR/ADMIN. Nhân viên
xem kết quả trên hệ thống, chưa có email/push hoặc phân công một HR cụ thể.

## Phạm vi và quy tắc

- Danh tính người gửi lấy từ `sub` và `employee_id` trong access JWT của Auth;
  request body không quyết định người gửi, trạng thái hay người duyệt.
- Loại nghỉ: `ANNUAL` (phép năm), `UNPAID` (không lương). Phép `ANNUAL` được cấp tự động
  12 ngày (24 đơn vị nửa ngày) cho mỗi nhân viên trong mỗi năm dương lịch.
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
  đơn đã duyệt/từ chối/rút không sửa được. Từ chối cần lý do; ghi chú duyệt là tùy chọn.
- Nhân viên chỉ xem đơn/lịch sử của mình. HR/ADMIN xem hàng chờ toàn công ty,
  nhưng không được duyệt hoặc từ chối đơn của chính mình. MANAGER chưa có quyền duyệt.
- Chặn khoảng thời gian trùng với đơn `PENDING`/`APPROVED` của cùng nhân viên, kể cả
  gửi đồng thời. Khóa hàng `leave_request_owners` trong transaction để tuần tự hóa
  kiểm tra và tạo đơn theo nhân viên. Hai đơn `MORNING` và `AFTERNOON` được phép
  cùng ngày; đơn bị từ chối/rút không chặn gửi lại.
- Duyệt/từ chối/rút dùng `If-Match: "<version>"`. Cập nhật có điều kiện theo
  version và trạng thái, chỉ một thao tác đồng thời thắng. Ghi lịch sử cùng
  transaction; lỗi ghi lịch sử sẽ rollback thay đổi.
- Chưa đồng bộ Kafka, Calendar hay Attendance. Chỉ `ANNUAL` dùng sổ số dư;
  `UNPAID` không trừ số dư phép năm.
  Khi bổ sung các consumer, triển khai outbox cùng transaction và chống trùng sự kiện.

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
./mvnw spring-boot:run
```

Database mới chỉ chạy `001_leave_schema.sql` hiện tại. Database đã có tính năng
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
| `GET /balance?year=2030` | Số dư phép năm của chính mình; mặc định là năm hiện tại |
| `GET /inbox?status=PENDING&page=0&size=20` | HR/ADMIN; bỏ status để xem mọi trạng thái |
| `GET /pending-count` | HR/ADMIN; số đơn đang chờ dùng cho badge thông báo trên menu |
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
# Tạo database test riêng và áp dụng docs/sql/001_leave_schema.sql trước.
LEAVE_TEST_DB_URL=jdbc:postgresql://localhost:5432/leave_test \
LEAVE_TEST_DB_USER=leave_test LEAVE_TEST_DB_PASSWORD=your-test-password ./mvnw test
```

Kiểm thử gồm workflow, quyền sở hữu, tự duyệt, version, xử lý đồng thời,
chống trùng khoảng ngày và rollback audit. Frontend có unit test và Playwright:
`npm run test`, `npx playwright test tests/e2e/leave.spec.js` trong `frontend`.
