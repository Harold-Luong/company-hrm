# API Gateway

Gateway là điểm truy cập duy nhất từ Internet. Gateway kiểm tra access JWT và
chuyển nguyên đường dẫn, query, body, `Authorization` đến service; các service
vẫn xác thực JWT và quyết định quyền nghiệp vụ như trước.

Trong repository hiện tại, **Workforce được ánh xạ tới `calendar-service`**.
Chưa có module Workforce riêng. Tên `workforce_db` chỉ dùng cho database mới trong
Compose; database `calendar_db` đang có trên máy không bị đổi tên hoặc chỉnh sửa.

| URL qua gateway | Service nội bộ |
| --- | --- |
| `/api/v1/auth/**` | Auth `:8081` |
| `/activate`, `/activation/index.html`, `/activation/activate.js`, `/activation/style.css` | Auth `:8081` |
| `/api/v1/employees/**`, `/api/v1/departments/**`, `/api/v1/positions/**` | Employee `:8082` |
| `/api/v1/calendar/**`, `/api/v1/calendar-events/**` | Workforce / Calendar `:8083` |
| `/api/v1/leave/**` | Leave `:8084` |
| `/api/v1/attendance/**` | Attendance `:8085`; hiện có health check, giữ nguyên đường dẫn |
| `GET /api/v1/leave/health-check` (cần access token) | `/actuator/health` của Leave `:8084` |
| `GET /api/v1/calendar/health-check` (cần access token) | `/actuator/health` của Calendar `:8083` |
| `GET /actuator/health` | Health của gateway, không trả chi tiết |

Chỉ các POST login, refresh, logout, activate và các GET trang activation,
`/actuator/health` của gateway cho phép truy cập ẩn danh. Health-check của Auth,
Employee, Calendar, Leave và Attendance cần access token. Register, logout-all, activation-invitations
và API nghiệp vụ yêu cầu access token. Refresh được Auth kiểm tra bằng refresh
token trong body, kể cả khi header còn mang access token hết hạn. Swagger và các
endpoint quản trị không được mở qua gateway.

JWT dùng RS256, public key tối thiểu 2048 bit, kiểm tra issuer, audience, thời hạn,
subject, employee UUID, roles và loại token theo hợp đồng Auth hiện có. Gateway
không cần private key, refresh key hoặc kết nối database.

## Chạy local

Yêu cầu Java 21, Maven và access public key đang được Auth sử dụng.
Từ thư mục `modules/gateway`:

```bash
mvn verify
JWT_ACCESS_PUBLIC_KEY=file:/absolute/path/to/access-public.pem mvn spring-boot:run
```

Mặc định gateway chỉ nghe `127.0.0.1:8080` qua HTTP để phát triển.
Khởi động Auth với `SERVER_PORT=8081` (Auth trước đây mặc định dùng 8080), Employee
với 8082 và Calendar với 8083. Để service local chỉ nhận kết nối từ máy này,
đặt `SERVER_ADDRESS=127.0.0.1` khi chạy từng service.

Có thể đổi đích qua `AUTH_SERVICE_URL`, `EMPLOYEE_SERVICE_URL`,
`WORKFORCE_SERVICE_URL`, `LEAVE_SERVICE_URL`, `ATTENDANCE_SERVICE_URL`.
Attendance mặc định tại `:8085`, dùng `attendance_db`; xem [hướng dẫn](../attendance-service/README.md).
Leave mặc định chạy tại `:8084`,
tự kiểm tra quyền người gửi/HR và sở hữu `leave_db` riêng.
`CORS_ALLOWED_ORIGINS` là danh sách origin phân cách bằng
dấu phẩy; mặc định `http://localhost:5173`. Frontend gọi gateway thay cho cổng
riêng của từng service và tiếp tục gửi `Authorization: Bearer <access-token>`.
CORS được xử lý tại gateway, gồm cả preflight và lỗi 401/403; gateway bỏ `Origin`
khi chuyển tiếp để tránh cấu hình CORS local của Calendar chặn frontend production.
Các header `ETag`, `Location`, `Retry-After`, `WWW-Authenticate` được expose.

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8080/api/v1/employees  # 401 nếu không có token
```

## HTTPS và mạng private

Dùng [hướng dẫn Compose](../infra/gateway/README.md). Profile `prod` bắt buộc
PKCS12 và mật khẩu TLS, nghe HTTPS trên 8443 trong container; Compose publish
cổng 443. Không có listener HTTP công khai.

Gateway nhận kết nối trực tiếp từ client và bỏ toàn bộ `Forwarded`,
`X-Forwarded-*`, `X-Real-IP` và các header danh tính không đáng tin, sau đó tạo
`X-Forwarded-For` / `X-Forwarded-Proto` từ kết nối thật. Compose bật xử lý forwarded
header ở Auth để login throttle lấy đúng IP. Mạng giữa service là vùng tin cậy;
không expose Auth ra ngoài hoặc gắn container không tin cậy vào mạng này.
Nếu thêm CDN/load balancer phía trước, cần thiết kế lại danh sách proxy tin cậy;
hiện tại IP được ghi nhận sẽ là IP của proxy đó.

Giới hạn login của Auth vẫn là bộ đếm trong bộ nhớ mỗi instance. Khi scale Auth
nhiều instance, cần bộ đếm dùng chung như mô tả trong
[login throttling](../auth-service-main/docs/login-throttling.md).

## Kiểm thử

`mvn verify` chạy gateway thật với bốn HTTP backend giả lập, RSA key sinh riêng
trong test: route, query/body/token, JWT sai chữ ký/claims/algorithm, endpoint
public/private, refresh với access token hết hạn, CORS, header giả và lỗi 403
của backend. Không cần database hoặc key production.

Cấu hình sử dụng namespace `spring.cloud.gateway.server.webflux` theo
[tài liệu Spring Cloud Gateway](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/configuration.html).
