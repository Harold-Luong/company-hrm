# Email kích hoạt tài khoản qua Resend

Auth tự tạo lời mời khi xử lý thành công `EmployeeAccountRequested`, nếu
`AUTH_ACTIVATION_ENABLED=true`. Account, hash token, mail outbox và kết quả Kafka
commit trong cùng transaction. Gửi HTTP tới Resend diễn ra ở worker sau commit;
lỗi Resend không xóa Account hoặc làm thất bại yêu cầu cấp Account đã commit.

## 1. Cấu hình và chạy

Chuẩn bị database/role theo [hướng dẫn Auth](hrm-auth.md#khởi-tạo-database-và-seed),
rồi chạy schema đầy đủ trước khi bật tính năng (đã bao gồm các bảng email):

```bash
cd back-end/modules/auth-service-main
psql -X -v ON_ERROR_STOP=1 -h localhost -U auth_user -d auth_db \
  -f docs/sql/001_auth_schema.sql
# Tùy chọn cho local: tạo account HR/Admin để gọi API gửi lại/kiểm tra lời mời.
psql -X -v ON_ERROR_STOP=1 -h localhost -U auth_user -d auth_db \
  -f docs/sql/002_auth_seed.sql
test -f .env || cp .env.example .env
```

Hai script SQL mới đã bao gồm toàn bộ schema/seed của Auth; không chạy thêm
migration Auth rời. Schema không reset database và không tự nâng cấp cấu trúc bảng
cũ. Nếu đã có schema đúng và account HR/Admin, bỏ qua các bước SQL tương ứng.

Seed mới có `admin@company.com` và `hr@company.com`, mật khẩu `Admin@123456`.
Account seed đã hoàn tất kích hoạt (`activation_pending=false`), nên **không dùng
account seed làm đối tượng gửi lời mời**. Dùng HR/Admin seed để cấp account cho
nhân viên khác (EMP004 chưa có account trong seed), rồi theo dõi email của account
mới đó. Chạy seed không gửi mail; seed chạy lại cũng không reset mật khẩu account cũ.

Chuẩn bị RSA keys theo [hướng dẫn JWT](asymmetric-jwt.md#tạo-khóa-cho-local) nếu chưa có.

Điền `.env` (đã bị gitignore) hoặc environment variables trong IDE:

| Biến | Giá trị / mục đích |
|---|---|
| `RESEND_API_KEY` | Thay `xxxx` bằng API key Resend của bạn; chỉ dùng ở backend |
| `RESEND_FROM` | Ví dụ `Company HRM <hrm@tenmiencuaban.com>`; dùng domain đã verify trong Resend |
| `AUTH_ACTIVATION_TOKEN_SECRET` | Chuỗi base64 từ `openssl rand -base64 32`; giữ ổn định, không dùng API key/JWT key làm secret này |
| `AUTH_ACTIVATION_ENABLED` | Đặt `true` sau khi có schema và cấu hình; mặc định `false` |
| `AUTH_ACTIVATION_TOKEN_TTL` | Mặc định `24h`, lớn hơn 0 và tối đa `7d` |
| `AUTH_ACTIVATION_FRONTEND_URL` | Local `http://localhost:8080/activate`; production `https://hrm.example.com/activate` |
| `HRM_EVENTS_ENABLED` | Đặt `true` ở Auth và Employee; Kafka phải chạy, các topic đã tạo |

Spring Boot không tự đọc `.env`. Nếu chạy bằng shell:

```bash
set -a
. ./.env
set +a
./mvnw spring-boot:run
```

Không đưa `.env`, API key hay secret vào git/frontend. Khi bật tính năng mà thiếu
key/sender/secret, Auth từ chối khởi động. Thay đổi biến môi trường cần restart.
Giữ cùng token secret trên mọi replica và qua các lần restart để email đang retry
vẫn chứa cùng token. Đổi secret cần hủy mail pending/token cũ và gửi lời mời mới.

Resend dùng `POST https://api.resend.com/emails`, `Authorization: Bearer ...`,
payload `from/to/subject/text`, có `Idempotency-Key` cố định cho từng lời mời.
Sender/domain phải đáp ứng giới hạn tài khoản Resend; cấu hình sai 4xx được ghi
`FAILED`, sửa cấu hình rồi gửi lời mời mới. API key được để placeholder để bạn tự điền;
không có lệnh tự gửi email thật trong bộ test.
Tham khảo [Send Email](https://resend.com/docs/api-reference/emails/send-email) và
[Idempotency keys](https://resend.com/docs/dashboard/emails/idempotency-keys).

## Kiểm tra cấu hình và cách chạy local

1. Chỉ điền API key và token secret vào `.env` hoặc environment của IDE. File
   `.env.example` là mẫu được commit, luôn giữ placeholder. Không copy đè `.env`
   đã cấu hình. Nếu key thật từng được commit, thu hồi key trên Resend và thay key
   mới trong `.env`; sửa file mẫu không xóa bí mật khỏi lịch sử Git. Với token
   secret đã lộ, thay secret và thu hồi lời mời cũ trước khi gửi lại.
2. Đặt `AUTH_ACTIVATION_ENABLED=true` trong `.env`. `false` không tạo lời mời tự động
   và không khởi tạo worker gửi mail. `HRM_EVENTS_ENABLED=true` cần có ở cả Auth
   và Employee cho luồng cấp account qua Kafka.
3. `RESEND_FROM` là **địa chỉ gửi**, không phải địa chỉ người nhận. Không dùng
   `...@gmail.com`: Resend cần domain do bạn sở hữu và đã xác minh. Nếu chỉ thử
   local, có thể dùng `Company HRM <onboarding@resend.dev>` và email Account nhận
   lời mời phải là email gắn với chính tài khoản Resend của bạn. Gửi cho người khác
   cần domain riêng đã verify. Xem [quy định gửi thử của Resend](https://resend.com/docs/knowledge-base/403-error-resend-dev-domain)
   và [xác minh domain](https://resend.com/docs/dashboard/domains/introduction).
4. `AUTH_ACTIVATION_TOKEN_TTL=1h` hợp lệ. Token secret phải giải mã base64 được ít
   nhất 32 byte. Có đủ bốn file JWT trong `keys/`; đường dẫn mặc định tính từ
   **working directory của Auth**, không phải thư mục gốc repository.
5. Khởi động PostgreSQL/Kafka; kiểm tra Kafka healthy và init topics đã hoàn tất
   theo [hướng dẫn Kafka](../../infra/kafka/README.md). Chạy `docs/sql/001_auth_schema.sql`
   trên database mới trước khi bật activation; schema này đã bao gồm bảng token
   và mail outbox. Có cổng 9092 chưa đủ chứng minh topics đã sẵn sàng.
6. Chạy Auth từ terminal mới hoặc dừng instance cũ trước khi restart:

   ```bash
   cd back-end/modules/auth-service-main
   set -a
   . ./.env
   set +a
   ./mvnw spring-boot:run
   ```

   Chạy `./mvnw spring-boot:run` đơn thuần **không đọc `.env`**. Nếu bấm Run trong
   IDE, đặt working directory là `back-end/modules/auth-service-main` và cấu hình
   các biến trong Run Configuration hoặc chức năng nạp env file của IDE. Việc
   `source .env` ở terminal khác không cập nhật environment của IDE đang mở.
7. Từ terminal khác, kiểm tra:

   ```bash
   curl --fail http://localhost:8080/api/v1/auth/health-check
   ```

   Kỳ vọng `status=UP`, `database=UP`. Endpoint này không kiểm tra Resend hoặc
   migration. Auth phục vụ Swagger tại `http://localhost:8080/swagger-ui.html`.
8. Khởi động Employee với `HRM_EVENTS_ENABLED=true`, gửi yêu cầu cấp account qua
   Employee. Nếu Account đã được tạo khi gửi mail còn tắt, Auth không tự tạo lại
   lời mời khi restart: đăng nhập HR/Admin và gọi
   `POST /api/v1/auth/activation-invitations/{employeeId}` trong Swagger.
9. Dùng `GET /api/v1/auth/activation-invitations/{employeeId}` để theo dõi. Không
   có lời mời trả `404`; `PENDING` chờ worker (poll mặc định 5 giây); `SENT` đã được
   Resend chấp nhận. `FAILED` với `RESEND_HTTP_401/403` cần kiểm tra key, sender,
   domain và giới hạn người nhận trong dashboard Resend. Sau khi sửa cấu hình,
   restart Auth rồi tạo lời mời mới; email đã `FAILED` không tự retry.

`http://localhost:8080/activate` trong email chỉ dùng được trên chính máy chạy
Auth. Khi người nhận mở bằng điện thoại/máy khác, cấu hình URL HTTPS truy cập được.
Không khởi động Auth để “chỉ kiểm tra cấu hình” nếu đã bật activation và có mail
pending mà bạn chưa muốn gửi: worker có thể gửi ngay khi ứng dụng chạy.

## 2. Link và trang đặt mật khẩu

```text
https://hrm.example.com/activate?token=<opaque-token>
```

Nhánh backend có sẵn `GET /activate` và assets `/activation/activate.js`,
`/activation/style.css`, không cần đăng nhập. Khi dùng reverse proxy, chuyển ba
đường dẫn này và `/api/v1/auth/*` đến Auth. Nếu frontend Vue cung cấp `/activate`,
frontend gọi API POST bên dưới và có thể thay trang độc lập này.

Trang không dùng dịch vụ bên ngoài, có `Referrer-Policy: no-referrer`, không lưu
token ở local/sessionStorage và xóa query khỏi address bar sau khi đọc vào bộ nhớ.
Reload trang đã xóa query cần mở lại liên kết từ email. Cấu hình proxy/access log
không ghi query của `/activate` hoặc body của API kích hoạt. **GET không tiêu thụ
token**; trình quét email có thể mở link mà không kích hoạt account.

Mật khẩu: ít nhất 12 ký tự, tối đa 72 byte UTF-8 để tránh cắt ngầm với BCrypt.
Form yêu cầu nhập lại mật khẩu. Sau thành công, người dùng đăng nhập qua giao diện HRM.
Tính năng này chỉ đặt mật khẩu lần đầu; chưa bổ sung quên mật khẩu hoặc đổi mật khẩu
cho account đã hoạt động.

## 3. API

### Kích hoạt — public

`POST /api/v1/auth/activate`

```json
{"token":"<token-từ-email>","password":"<mật-khẩu-mới>"}
```

- `200`: account đã active, có thể đăng nhập. Không trả access/refresh token.
- `400`: token sai/hết hạn/đã dùng/đã thu hồi, account không chờ kích hoạt hoặc mật khẩu không hợp lệ.
- `503`: tính năng chưa bật.

Account row được khóa trước khi tiêu thụ token. Lưu password hash, bật active,
bỏ activation_pending, đánh dấu token dùng, thu hồi các lời mời còn lại và ghi
Outbox `AccountStatusChanged` cùng transaction. Hai request đồng thời chỉ có một
request thành công. Mất response có thể đã kích hoạt thành công; thử đăng nhập
bằng mật khẩu vừa đặt trước khi yêu cầu lời mời khác.

### Gửi lại lời mời — HR hoặc ADMIN

`POST /api/v1/auth/activation-invitations/{employeeId}` với Bearer access token.
Không có body/email tùy chọn: gửi tới email của Account trong Auth, không lấy email
liên hệ có thể đã đổi ở Employee.

- `202`: `{ "invitationId": "UUID", "deliveryStatus": "PENDING", "expiresAt": "..." }`.
- `404`: Account không tồn tại; `409`: Account không chờ kích hoạt.
- `429` + `Retry-After`: mỗi account có cooldown 60 giây, áp dụng qua database lock.
- `401/403`: chưa xác thực/không có quyền; `503`: chưa bật tính năng.

Lời mời mới thu hồi token cũ và hủy email pending cũ. Không tự kích hoạt lại account
đã active/bị khóa. Account tạo trước khi bật gửi mail dùng API này để nhận lời mời.
POST gửi lại không tự retry; nếu mất response, đọc trạng thái lời mời trước.

### Kiểm tra email gần nhất — HR hoặc ADMIN

`GET /api/v1/auth/activation-invitations/{employeeId}` với Bearer access token.

Trả `invitationId`, `deliveryStatus`, `expiresAt`, `sentAt`, `attempts`, `errorCode`.
`PENDING/SENT/FAILED/CANCELLED` là trạng thái gửi mail; `SENT` nghĩa là Resend đã
chấp nhận email, **không chứng minh inbox đã nhận hoặc account đã kích hoạt**.
Không có lời mời trả `404`. Không trả token, API key hay nội dung mail.

## 4. Lưu trữ, retry và đồng bộ

- `account_activation_tokens`: user_id, token_hash SHA-256, created_at, expires_at,
  used_at, revoked_at. Token là HMAC-SHA256 domain-separated của UUID lời mời với
  secret riêng, dạng base64url 256-bit; người không có secret không suy ra token
  từ UUID. Chỉ hash dùng để tra cứu/xác minh. Không lưu raw token trong database.
- `activation_mail_outbox`: lưu snapshot recipient, sender, URL **chưa có token**,
  trạng thái và lease. Worker dựng lại cùng token/payload cho lần retry sau crash;
  không ghi token hoặc password lên Kafka/log. Template v1 phải giữ ổn định cho
  lời mời đang pending để đáp ứng cùng payload khi dùng idempotency key.
- Worker claim bằng CAS, lease 2 phút; HTTP timeout 15 giây, connect timeout 5 giây.
  Ack/retry chỉ có hiệu lực với worker còn giữ lease; worker cũ không ghi đè worker mới.
- Mạng lỗi, 408/409/429/5xx retry có backoff và `Retry-After`; 4xx còn lại terminal.
  Riêng 409 `invalid_idempotent_request` (key cũ với payload khác) terminal.
  Dừng gửi khi token hết hạn/thu hồi/đã dùng. Cửa sổ gửi tối đa **23 giờ từ lúc tạo**,
  nằm trong thời gian giữ idempotency key 24 giờ của Resend; quá cửa sổ cần lời mời mới.
- Kích hoạt phát `AccountStatusChanged` với version tăng dần trên
  `hrm.auth.account-lifecycle.v1`. Employee group `employee-account-lifecycle-v1`
  cập nhật account_status; event trùng/cũ không ghi đè version mới, kể cả khi
  AccountCreated đến sau AccountStatusChanged.
- Race với Resend có thể khiến email cũ đã in-flight vẫn được gửi sau resend;
  token cũ đã bị thu hồi nên không thể kích hoạt. Không có đảm bảo inbox exactly-once.
- Kafka chưa chạy: activation đã commit vẫn có hiệu lực; sự kiện còn trong event_outbox
  chờ gửi. Employee sẽ cập nhật khi Kafka/consumer phục hồi.
- Dọn token/mail cũ theo chính sách lưu trữ (xóa mail trước token). Không cần cron
  xóa đúng thời điểm để bảo đảm hết hạn: API kiểm tra expires_at khi sử dụng.

## 5. Kiểm tra

```bash
./mvnw test
# Từ employee-service:
./mvnw test
```

Test dùng H2 và HTTP client giả lập: transaction rollback, kích hoạt/login, hết hạn,
revocation, dùng một lần/concurrency, quyền gửi lại/cooldown, mail claim/retry,
Resend payload/idempotency, và thứ tự sự kiện Kafka. Không gửi mail thật.
Sau khi tự điền key/domain, chạy thử bằng tài khoản development và kiểm tra
trạng thái mail trong API cùng dashboard Resend.
