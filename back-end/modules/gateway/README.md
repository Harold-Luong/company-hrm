# API Gateway

**Mục lục**

- [Cấu hình và chạy độc lập](#cấu-hình-và-chạy-độc-lập)
- [Chạy local](#chạy-local)
- [HTTPS và mạng private](#https-và-mạng-private)
- [Kiểm thử](#kiểm-thử)
- [Triển khai HRM qua gateway](#triển-khai-hrm-qua-gateway)

Gateway là điểm truy cập duy nhất từ Internet. Gateway kiểm tra access JWT và
chuyển nguyên đường dẫn, query, body, `Authorization` đến service; các service
vẫn xác thực JWT và quyết định quyền nghiệp vụ như trước.

Trong repository hiện tại, **Workforce được ánh xạ tới `calendar-service`**.
Chưa có module Workforce riêng. Tên `workforce_db` chỉ dùng cho database mới trong
Compose; database `calendar_db` đang có trên máy không bị đổi tên hoặc chỉnh sửa.

| URL qua gateway | Service nội bộ |
| --- | --- |
| `/api/v1/auth/**` | Auth `:8081` |
| `/activate`, `/activation/index.html` (link cũ, chuyển hướng đến Vue) | Auth `:8081` |
| `/api/v1/employees/**`, `/api/v1/departments/**`, `/api/v1/positions/**` | Employee `:8082` |
| `/api/v1/calendar/**`, `/api/v1/calendar-events/**` | Workforce / Calendar `:8083` |
| `/api/v1/leave/**` | Leave `:8084` |
| `/api/v1/attendance/**` | Attendance `:8085`, giữ nguyên đường dẫn |
| `/api/v1/notifications/**` | Notification `:8086`; `/stream` có route SSE riêng, không giới hạn response timeout |
| `GET /api/v1/leave/health-check` (cần access token) | `/actuator/health` của Leave `:8084` |
| `GET /api/v1/calendar/health-check` (cần access token) | `/actuator/health` của Calendar `:8083` |
| `GET /actuator/health` | Health của gateway, không trả chi tiết |

Chỉ các POST login, refresh, logout, activate và các GET chuyển hướng activation,
`/actuator/health` của gateway cho phép truy cập ẩn danh. Health-check của Auth,
Employee, Calendar, Leave và Attendance cần access token. Register, logout-all, activation-invitations
và API nghiệp vụ yêu cầu access token. Refresh được Auth kiểm tra bằng refresh
token trong body, kể cả khi header còn mang access token hết hạn. Swagger và các
endpoint quản trị không được mở qua gateway.

JWT dùng RS256, public key tối thiểu 2048 bit, kiểm tra issuer, audience, thời hạn,
subject, employee UUID, roles và loại token theo hợp đồng Auth hiện có. Gateway
không cần private key, refresh key hoặc kết nối database.

Chạy native với `.env` riêng như bên dưới; Gateway không có datasource.
Mẫu public key dùng `file:./keys/access-public.pem` từ thư mục Gateway; đổi sang đường dẫn tuyệt đối nếu IDE dùng working directory khác.

## Cấu hình và chạy độc lập

Project Maven riêng, Java 21, cổng mặc định 8080. Build/chạy từ thư mục
gateway; không cần chạy Maven ở gốc repository hoặc build service khác.

Điều kiện: Access public key và URL backend. Không cần database/Kafka; backend đích cần sẵn sàng khi gọi route tương ứng.

1. Chuẩn bị public key theo cấu hình bên dưới.
2. Tạo `.env` từ [.env.example](.env.example) nếu chưa có, rồi sửa host/port,
   mật khẩu, đường dẫn khóa và các cờ tính năng theo môi trường thực tế.
3. Nạp biến vào đúng terminal hoặc Run Configuration của IDE trước khi chạy.

Các lệnh sau dùng Git Bash/Bash, bắt đầu từ gốc repository:

```bash
cd back-end/modules/gateway
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

| Biến môi trường | Cấu hình cho project này |
| --- | --- |
| `SERVER_PORT` | 8080 |
| `GATEWAY_BIND_ADDRESS` | 127.0.0.1 khi chạy local |
| `JWT_ACCESS_PUBLIC_KEY` | file:/duong-dan-tuyet-doi/access-public.pem |
| `JWT_ACCESS_ISSUER` / `JWT_ACCESS_AUDIENCE` | auth-service / hrm-api-access, khớp Auth |

Chỉ cần access public key của Auth; không cấp private key cho project này.

Dừng bằng `Ctrl+C` trong terminal này. Build riêng bằng `mvn package`; kiểm thử theo
phần kiểm thử bên dưới. Với backend có wrapper có thể dùng `./mvnw` hoặc `mvnw.cmd`
thay mvn. Không bắt buộc `run-all.sh` hoặc Docker Compose để chạy project.

## Chạy local

Yêu cầu Java 21, Maven và access public key đang được Auth sử dụng.
Từ thư mục `modules/gateway`:

```bash
mvn verify
JWT_ACCESS_PUBLIC_KEY=file:/absolute/path/to/access-public.pem mvn spring-boot:run
```

Mặc định gateway chỉ nghe `127.0.0.1:8080` qua HTTP để phát triển.
Auth mặc định chạy `8081`, Employee
với 8082 và Calendar với 8083. Để service local chỉ nhận kết nối từ máy này,
đặt `SERVER_ADDRESS=127.0.0.1` khi chạy từng service.

Có thể đổi đích qua `AUTH_SERVICE_URL`, `EMPLOYEE_SERVICE_URL`,
`WORKFORCE_SERVICE_URL`, `LEAVE_SERVICE_URL`, `ATTENDANCE_SERVICE_URL`, `NOTIFICATION_SERVICE_URL`.
Notification mặc định `:8086`; Compose hiện chưa triển khai service này. Xem [Notification](../notification-service/README.md).
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

Dùng [hướng dẫn Compose](#deployment). Profile `prod` bắt buộc
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
[login throttling](../auth-service-main/README.md#throttling).

## Kiểm thử

`mvn verify` chạy gateway thật với bốn HTTP backend giả lập, RSA key sinh riêng
trong test: route, query/body/token, JWT sai chữ ký/claims/algorithm, endpoint
public/private, refresh với access token hết hạn, CORS, header giả và lỗi 403
của backend. Không cần database hoặc key production.

Cấu hình sử dụng namespace `spring.cloud.gateway.server.webflux` theo
[tài liệu Spring Cloud Gateway](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/configuration.html).

<a id="deployment"></a>

## Triển khai HRM qua gateway

Để chạy bộ local gồm frontend và dữ liệu demo (chưa có Notification), dùng
[Compose tại thư mục gốc](../../../README.md#docker).

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

Bộ HTTPS này có Gateway và 5 service nghiệp vụ (Auth, Employee, Calendar/Workforce, Leave, Attendance). Notification đã có mã nguồn/route nhưng chưa được triển khai trong Compose này; không bật thông báo Kafka trước khi bổ sung database, schema outbox, topic, service và `NOTIFICATION_SERVICE_URL`. Xem [Notification](../notification-service/README.md).

<a id="deployment-chuẩn-bị"></a>

### Chuẩn bị

Yêu cầu Docker Engine và Compose hỗ trợ `!reset` (Compose 2.24 trở lên).
Chạy các lệnh sau tại `back-end/modules/infra/gateway` (thư mục chứa Compose HTTPS):

```bash
cp .env.example .env
```

Điền `.env` với năm mật khẩu database riêng (gồm `LEAVE_DB_PASSWORD` và
`ATTENDANCE_DB_PASSWORD`), origin HTTPS của frontend và đường
dẫn tuyệt đối tới các key/certificate. Không commit `.env` hoặc secrets.

- Dùng hai cặp RSA **khác nhau** của Auth: access và refresh. Private key PKCS#8,
  public key X.509, tối thiểu 2048 bit. Nếu đã có key, dùng lại để token còn hiệu lực.
  Cách tạo key có trong [tài liệu Auth](../auth-service-main/README.md#jwt).
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

<a id="deployment-khởi-động"></a>

### Khởi động

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
database phát triển khi chấp nhận mất dữ liệu theo [database Attendance](../attendance-service/README.md#database).
Database cần giữ dữ liệu phải có kế hoạch migration riêng.
Cấu hình `ATTENDANCE_ALLOWED_NETWORKS` là IP/CIDR client công ty mà Gateway nhìn thấy,
`ATTENDANCE_TRUSTED_PROXIES` là địa chỉ/CIDR riêng của Gateway. Danh sách rỗng sẽ
từ chối chấm công; không dùng subnet Docker làm mạng client công ty.
Xem [hướng dẫn Attendance](../attendance-service/README.md).

Không nạp seed chứa tài khoản/mật khẩu mẫu lên hệ thống public. Production cần
quy trình cấp tài khoản quản trị phù hợp trước khi người dùng đăng nhập.

<a id="deployment-kiểm-tra"></a>

### Kiểm tra

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

<a id="deployment-mail-activation-và-kết-nối-ra-ngoài"></a>

### Mail activation và kết nối ra ngoài

Mạng `internal` chặn Internet outbound từ các backend. `AUTH_ACTIVATION_ENABLED`
để `false`, phù hợp mặc định hiện có. Nếu triển khai email activation qua Resend,
cần cấp đường egress có kiểm soát cho Auth, cấu hình secret, sender, API key và
`AUTH_ACTIVATION_FRONTEND_URL=https://<frontend>/activate` theo
[tài liệu activation](../auth-service-main/README.md#activation), rồi bật
feature. Không bật mail trong cấu hình này khi chưa có egress.

Workforce ở đây là `calendar-service`; route API vẫn giữ `/api/v1/calendar` và
`/api/v1/calendar-events`. Khi có Workforce riêng, thay build/URL và thêm route
phù hợp. Các bảng Calendar hiện có khởi tạo trong `workforce_db` mới.
