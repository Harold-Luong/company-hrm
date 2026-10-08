# Triển khai HRM qua gateway

Để chạy toàn bộ hệ thống local cùng frontend và dữ liệu demo, dùng
[Compose tại thư mục gốc](../../../../DOCKER.md).

```text
Internet --HTTPS :443--> Gateway
                           |
                    services (internal)
                  /      |       |        |          \
                Auth  Employee Workforce Leave    Attendance
                  |      |       |        |          |
               auth_db employee_db workforce_db leave_db attendance_db
```

Workforce trong cấu hình này là `calendar-service`.

Mỗi database nằm trên một network `internal` riêng với service sở hữu nó.
Gateway không tham gia mạng database. Auth, Employee, Workforce, Leave, Attendance và Kafka không
publish cổng nào. Kafka được tái sử dụng từ `infra/kafka/compose.yaml`, bỏ cổng
localhost được publish trong cấu hình phát triển. Chỉ gateway có network public.

## Chuẩn bị

Yêu cầu Docker Engine và Compose hỗ trợ `!reset` (Compose 2.24 trở lên).
Chạy các lệnh sau tại `modules/infra/gateway`:

```bash
cp .env.example .env
```

Điền `.env` với năm mật khẩu database riêng (gồm `LEAVE_DB_PASSWORD` và
`ATTENDANCE_DB_PASSWORD`), origin HTTPS của frontend và đường
dẫn tuyệt đối tới các key/certificate. Không commit `.env` hoặc secrets.

- Dùng hai cặp RSA **khác nhau** của Auth: access và refresh. Private key PKCS#8,
  public key X.509, tối thiểu 2048 bit. Nếu đã có key, dùng lại để token còn hiệu lực.
  Cách tạo key có trong [tài liệu Auth](../../auth-service-main/docs/asymmetric-jwt.md).
- Gateway, Employee, Workforce, Leave và Attendance chỉ nhận access public key. Chỉ Auth nhận private
  keys và refresh public key.
- Dùng chứng chỉ TLS hợp lệ cho domain gateway, đóng gói PKCS12. Ví dụ chuyển từ
  PEM (OpenSSL sẽ hỏi mật khẩu, điền cùng mật khẩu vào `.env`):

```bash
openssl pkcs12 -export -name gateway \
  -in fullchain.pem -inkey privkey.pem -out gateway.p12
```

Container Java chạy UID/GID `10001:10001`. Compose secret dạng file là bind mount;
file phải đọc được bởi UID này. Có thể tạo bản sao chuyên dùng trên máy triển khai
bằng `sudo install -o 10001 -g 10001 -m 0400 <source> <destination>` cho từng file
và trỏ `.env` tới các bản sao đó. Không thay quyền key gốc đang dùng ở local.

## Khởi động

```bash
docker compose --env-file .env config --quiet
docker compose --env-file .env up -d --build
docker compose ps
docker compose logs --tail=100 gateway auth employee workforce leave attendance
```

Các volume mang tên project `hrm`, không dùng database đang chạy trên localhost.
Schema SQL hiện có được chạy tự động khi database volume còn trống; Hibernate
vẫn dùng `validate`. Không tự seed tài khoản với mật khẩu mẫu. Với volume đã có
dữ liệu, script init không chạy lại: áp dụng SQL migration phù hợp trước khi
khởi động phiên bản mới. Không dùng `down -v` để nâng cấp vì sẽ xóa dữ liệu.

Đây là cấu hình một host, một instance mỗi service. Gateway có thể khởi động
trước khi ứng dụng backend sẵn sàng; chờ log khởi động thành công trước khi đưa
traffic vào. `/actuator/health` chỉ báo trạng thái gateway.

Để thử login trên môi trường **local dùng riêng cho phát triển**, có thể nạp seed
đã có trong repository theo thứ tự:

```bash
docker compose exec -T employee-db psql -U employee_user -d employee_db -v ON_ERROR_STOP=1 < ../../employee-service/docs/sql/002_employee_seed.sql
docker compose exec -T auth-db psql -U auth_user -d auth_db -v ON_ERROR_STOP=1 < ../../auth-service-main/docs/sql/002_auth_seed.sql
```

Leave chạy tại `leave:8084`, dùng database riêng `leave_db`; Gateway chuyển
`/api/v1/leave/**` tới Leave. Schema `leave_requests`, `leave_request_history` và
`leave_request_owners` được khởi tạo trên volume mới. Chưa có seed đơn nghỉ.
Leave chỉ nhận access public key, không tham gia mạng dữ liệu của service khác.

Attendance chạy tại `attendance:8085`, sở hữu `attendance_db`; Gateway giữ nguyên
đường dẫn `/api/v1/attendance/**`. Đã có API ca cố định, phân công, chấm công và CSV tạm tính. Database mới được init bằng
`001_attendance_schema.sql`. Khi đổi cấu trúc trong giai đoạn phát triển, tạo lại
database Attendance theo README SQL; không có chuỗi migration.
Cấu hình `ATTENDANCE_ALLOWED_NETWORKS` là IP/CIDR client công ty mà Gateway nhìn thấy,
`ATTENDANCE_TRUSTED_PROXIES` là địa chỉ/CIDR riêng của Gateway. Danh sách rỗng sẽ
từ chối chấm công; không dùng subnet Docker làm mạng client công ty.
Xem [hướng dẫn Attendance](../../attendance-service/README.md).

Không nạp seed chứa tài khoản/mật khẩu mẫu lên hệ thống public. Production cần
quy trình cấp tài khoản quản trị phù hợp trước khi người dùng đăng nhập.

## Kiểm tra

```bash
curl https://api.example.com/actuator/health
curl -i https://api.example.com/api/v1/employees
# Không có access token: HTTP 401.

docker compose ps
# Chỉ gateway có cổng host 443; service, database và Kafka không có port binding.
```

Mở inbound TCP 443 trên firewall/security group của host. Không mở 8081–8085,
5432 hoặc 9092. Quản trị database bằng `docker compose exec`, không thêm `ports`
vào các database/service.

## Mail activation và kết nối ra ngoài

Mạng `internal` chặn Internet outbound từ các backend. `AUTH_ACTIVATION_ENABLED`
để `false`, phù hợp mặc định hiện có. Nếu triển khai email activation qua Resend,
cần cấp đường egress có kiểm soát cho Auth, cấu hình secret, sender, API key và
`AUTH_ACTIVATION_FRONTEND_URL=https://<frontend>/activate` theo
[tài liệu activation](../../auth-service-main/docs/ACCOUNT-ACTIVATION.md), rồi bật
feature. Không bật mail trong cấu hình này khi chưa có egress.

Workforce ở đây là `calendar-service`; route API vẫn giữ `/api/v1/calendar` và
`/api/v1/calendar-events`. Khi có Workforce riêng, thay build/URL và thêm route
phù hợp. Các bảng Calendar hiện có khởi tạo trong `workforce_db` mới.
