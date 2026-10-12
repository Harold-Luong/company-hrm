# Notification Service

**Mục lục**

- [Cấu hình và chạy độc lập](#cấu-hình-và-chạy-độc-lập)
- [Database](#database)
- [Kiểm tra và build](#kiểm-tra-và-build)
- [Bật luồng thông báo](#bật-luồng-thông-báo)
- [API](#api)
- [Phạm vi triển khai](#phạm-vi-triển-khai)

Backend Spring Boot/Java 21 tại cổng `8086`, dùng PostgreSQL `notification_db`.
Nhận thông báo qua Kafka từ luồng đơn nghỉ phép và đơn đi trễ/về sớm, lưu bền
vững và cung cấp API đọc/đánh dấu đã đọc cùng SSE. Chưa có tích hợp Notification
API/SSE vào frontend, gửi email/push hoặc sự kiện từ luồng điều chỉnh công/OT.

## Cấu hình và chạy độc lập

Project Maven riêng, Java 21, cổng mặc định 8086. Build/chạy từ thư mục
notification-service; không cần chạy Maven ở gốc repository hoặc build service khác.

Điều kiện: PostgreSQL/schema và access public key. Đặt `HRM_NOTIFICATIONS_ENABLED=false` để chạy API với dữ liệu hiện có; nhận sự kiện mới cần Kafka/topic và producer Leave/Attendance.

1. Chuẩn bị database/user riêng và SQL theo phần database trong `README` này.
2. Tạo `.env` từ [.env.example](.env.example) nếu chưa có, rồi sửa host/port,
   mật khẩu, đường dẫn khóa và các cờ tính năng theo môi trường thực tế.
3. Nạp biến vào đúng terminal hoặc Run Configuration của IDE trước khi chạy.

Các lệnh sau dùng Git Bash/Bash, bắt đầu từ gốc repository:

```bash
cd back-end/modules/notification-service
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
| `SERVER_PORT` | 8086 |
| `SPRING_DATASOURCE_URL` | jdbc:postgresql://localhost:5432/notification_db; sửa port nếu DB dùng 55432 hoặc port khác |
| `SPRING_DATASOURCE_USERNAME` | notification_user |
| `SPRING_DATASOURCE_PASSWORD` | Mật khẩu của user database này |
| `JWT_ACCESS_PUBLIC_KEY` | file:/duong-dan-tuyet-doi/access-public.pem |
| `JWT_ACCESS_ISSUER` / `JWT_ACCESS_AUDIENCE` | auth-service / hrm-api-access, khớp Auth |

Chỉ cần access public key của Auth; không cấp private key cho project này.

`SPRING_DATASOURCE_*` ưu tiên hơn biến `<SERVICE>_DB_*`; `SERVER_PORT` ưu tiên hơn
biến cổng riêng nếu có. Không giữ hai bộ giá trị mâu thuẫn trong environment.
Dừng bằng `Ctrl+C` trong terminal này. Module chưa có Maven Wrapper; dùng Maven
đã cài. Không bắt buộc `run-all.sh` hoặc Docker Compose để chạy project.

## Database

Tạo user và database mới bằng tài khoản quản trị PostgreSQL, chạy từng lệnh ngoài
transaction. Bỏ qua nếu đã tồn tại và dùng mật khẩu thực tế của môi trường:

```sql
CREATE USER notification_user WITH PASSWORD 'notification_password';
CREATE DATABASE notification_db OWNER notification_user;
```

Từ thư mục module, áp schema trước khi chạy theo phần cấu hình phía trên:

```bash
psql -X -h localhost -p 5432 -U notification_user -d notification_db -v ON_ERROR_STOP=1 -f docs/sql/001_notification_schema.sql
```

Khởi tạo database mới bằng [001_notification_schema.sql](docs/sql/001_notification_schema.sql).
Schema gồm `notifications` và `notification_reads`. Cấu hình datasource mặc định
dùng `NOTIFICATION_DB_URL`, `NOTIFICATION_DB_USER`, `NOTIFICATION_DB_PASSWORD`
và PostgreSQL `5432`; mẫu `.env` dùng `SPRING_DATASOURCE_*` để override sang
cluster local `55432`. `SERVER_PORT` trong `.env` override cổng mặc định.

## Kiểm tra và build

Trong thư mục module, chạy `mvn package`. Hiện chưa có `src/test`; build thành
công chưa xác nhận nghiệp vụ Kafka/SSE. Sau khi khởi động với cấu hình thực tế,
kiểm tra `GET http://localhost:8086/actuator/health` và các API có Bearer token.

## Bật luồng thông báo

1. Áp [outbox Leave](../leave-service/docs/sql/004_notification_outbox.sql) vào
   database Leave và [outbox Attendance](../attendance-service/docs/sql/004_notification_outbox.sql)
   vào database Attendance.
2. Chuẩn bị Kafka và topic `hrm.requests.notifications.v1`. Bộ init Kafka hiện
   chưa tự tạo topic này. Với [Compose Kafka riêng](../../../README.md#kafka),
   chạy từ thư mục gốc:

   ```bash
   docker compose -f back-end/modules/infra/kafka/compose.yaml exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:19092 --create --if-not-exists --topic hrm.requests.notifications.v1 --partitions 1 --replication-factor 1
   ```

3. Đặt `KAFKA_BOOTSTRAP_SERVERS` cùng broker và `HRM_NOTIFICATIONS_ENABLED=true`
   ở Leave, Attendance và Notification; mặc định ứng dụng là `false`.
4. Gateway cần `NOTIFICATION_SERVICE_URL=http://127.0.0.1:8086` khi chạy native.

Producer ghi outbox cùng transaction nghiệp vụ rồi publish theo lịch. Consumer
dùng group `hrm-notifications-v1`, chống trùng theo `eventId`; lỗi được retry,
chưa có dead-letter topic. Notification không tự cập nhật coverage của Attendance.

## API

Tất cả API yêu cầu Bearer access JWT của Auth; prefix `/api/v1/notifications`.

| Method / đường dẫn | Hành vi |
| --- | --- |
| `GET /api/v1/notifications?before=<cursor>` | Trang tối đa 20 mục, trả `nextBefore` và `unreadCount` |
| `PATCH /api/v1/notifications/{id}/read` | Đánh dấu một thông báo đã đọc, trả `204` |
| `POST /api/v1/notifications/read-all` | Body `{"through": <cursor>}`, đánh dấu đã đọc tới cursor, trả `204` |
| `GET /api/v1/notifications/stream` | SSE có xác thực; Gateway đã có route stream riêng |

Thông báo được lọc theo người gửi đơn hoặc quyền duyệt HR/ADMIN; hành động của
chính người đang xem không được tính như thông báo cho họ. Trạng thái đã đọc
được lưu theo người dùng.

SSE gửi event `notifications` với `{revision, readCount, unreadCount}` khi snapshot
thay đổi, còn lại gửi heartbeat. Stream đóng khi JWT hết hạn hoặc sau khoảng
60 giây; client cần kết nối lại với Bearer token hợp lệ và gọi API danh sách để
lấy nội dung. Giới hạn 8 kết nối/người dùng và 500 kết nối/instance; vượt giới hạn
trả `429`. Frontend HR/ADMIN dùng stream này để cập nhật badge hàng chờ nghỉ phép
và đi trễ/về sớm; tự kết nối lại với Bearer token và dừng kết nối khi đăng xuất.

## Phạm vi triển khai

`run-all.sh` đã chạy Notification. Hai bộ Docker Compose hiện chưa có service,
database, schema và biến đích Gateway cho Notification; cần bổ sung triển khai
trước khi bật tính năng trong Docker. Sự kiện Notification kích hoạt tải lại
`pending-count` từ Leave và Attendance; badge hiển thị số đơn chờ duyệt. Nếu
Notification chưa sẵn sàng, frontend vẫn cập nhật các số này mỗi 30 giây.
