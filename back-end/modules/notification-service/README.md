# Notification Service

Chạy bằng container riêng: dùng `Dockerfile`, `compose.yaml` và
`.env.docker.example` tại thư mục này. Xem [hướng dẫn Docker độc lập](../../../README.md#docker-independent)
để chuẩn bị network, cấu hình kết nối, build/start/stop riêng hoặc phát hành image.

**Mục lục**

- [Cấu hình và chạy độc lập](#cấu-hình-và-chạy-độc-lập)
- [Database](#database)
- [Kiểm tra và build](#kiểm-tra-và-build)
- [Bật luồng thông báo](#bật-luồng-thông-báo)
- [API](#api)
- [Phạm vi triển khai](#phạm-vi-triển-khai)

Backend Spring Boot/Java 21 tại cổng `8086`, dùng PostgreSQL `notification_db`.
Nhận sự kiện Kafka từ luồng đơn nghỉ phép, đi trễ/về sớm và kết quả cấp tài khoản.
Frontend dùng SSE để cập nhật badge, lịch nghỉ phép và kết quả cấp tài khoản mà
không tải lại trang. Kết quả cấp tài khoản hiện toast cho đúng HR/Admin đã yêu cầu,
kể cả khi đã chuyển trang. Chưa có giao diện danh sách thông báo/đánh dấu đã đọc,
gửi push hoặc sự kiện từ điều chỉnh công/OT. Email kích hoạt vẫn do Auth gửi.

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

SSE đang mở kiểm tra lại phiên trước mỗi đợt gửi; khi Auth từ chối hoặc không
sẵn sàng, stream đóng. Request/đợt gửi đã qua kiểm tra có thể hoàn tất.

## Cấu hình và chạy độc lập

Project Maven riêng, Java 21, cổng mặc định 8086. Build/chạy từ thư mục
notification-service; không cần chạy Maven ở gốc repository hoặc build service khác.

Điều kiện: PostgreSQL/schema và access public key. Đặt `HRM_NOTIFICATIONS_ENABLED=false` để chạy API với dữ liệu hiện có; nhận sự kiện mới cần Kafka/topic và producer Leave/Attendance/Auth.

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
biến môi trường riêng; thay `.env` phải nạp lại và restart project đó. Mẫu dùng
bản sao access public key tại `keys/access-public.pem` của chính service, tính
từ working directory của module. Cấp cùng public key của môi trường Auth đang dùng
cho từng service khi triển khai; service không cần thư mục Auth trên cùng máy.

| Biến môi trường                             | Cấu hình cho project này                                                                    |
| ------------------------------------------- | ------------------------------------------------------------------------------------------- |
| `SERVER_PORT`                               | 8086                                                                                        |
| `SPRING_DATASOURCE_URL`                     | jdbc:postgresql://localhost:5432/notification_db; sửa port nếu DB dùng 55432 hoặc port khác |
| `SPRING_DATASOURCE_USERNAME`                | notification_user                                                                           |
| `SPRING_DATASOURCE_PASSWORD`                | Mật khẩu của user database này                                                              |
| `JWT_ACCESS_PUBLIC_KEY`                     | file:/duong-dan-tuyet-doi/access-public.pem                                                 |
| `JWT_ACCESS_ISSUER` / `JWT_ACCESS_AUDIENCE` | auth-service / hrm-api-access, khớp Auth                                                    |

Chỉ cần access public key của Auth; không cấp private key cho project này.

`SPRING_DATASOURCE_*` ưu tiên hơn biến `<SERVICE>_DB_*`; `SERVER_PORT` ưu tiên hơn
biến cổng riêng nếu có. Không giữ hai bộ giá trị mâu thuẫn trong environment.
Dừng bằng `Ctrl+C` trong terminal này. Module chưa có Maven Wrapper; dùng Maven
đã cài. Không bắt buộc `run-all.sh` hoặc Docker Compose để chạy project.

## Database

Tạo user và database mới bằng tài khoản quản trị PostgreSQL, chạy từng lệnh ngoài
transaction. Bỏ qua nếu đã tồn tại và dùng mật khẩu thực tế của môi trường:

```sql
CREATE USER notification_user WITH PASSWORD 'notification_pw';
CREATE DATABASE notification_db OWNER notification_user;
```

Từ thư mục module, áp schema trước khi chạy theo phần cấu hình phía trên:

```bash
psql -X -h localhost -p 5432 -U notification_user -d notification_db -v ON_ERROR_STOP=1 -f docs/sql/001_notification_schema.sql
psql -X -h localhost -p 5432 -U notification_user -d notification_db -v ON_ERROR_STOP=1 -f docs/sql/002_account_result_notifications.sql
```

Khởi tạo database mới bằng [001_notification_schema.sql](docs/sql/001_notification_schema.sql).
Áp thêm [002_account_result_notifications.sql](docs/sql/002_account_result_notifications.sql)
cho cả database mới và database đang dùng, trước khi restart Notification.
Schema gồm `notifications`, `notification_reads` và `account_result_notifications`.
Bảng kết quả cấp tài khoản lưu người nhận, chống trùng theo event/request và cursor
để phát tiếp khi SSE kết nối lại; không tham gia số thông báo chưa đọc của đơn từ.
Cấu hình datasource mặc định
dùng `NOTIFICATION_DB_URL`, `NOTIFICATION_DB_USER`, `NOTIFICATION_DB_PASSWORD`
và PostgreSQL `5432`; mẫu `.env` dùng `SPRING_DATASOURCE_*` để override sang
cluster local `55432`. `SERVER_PORT` trong `.env` override cổng mặc định.

## Kiểm tra và build

Trong thư mục module, chạy `mvn package`. Test H2 kiểm tra lưu/chống trùng kết quả,
phân quyền người nhận và phân trang phát lại; khóa transaction riêng của PostgreSQL
không được thực thi trong test H2. Frontend có test SSE, polling dự phòng, chuyển
trang và chống toast trùng. Các test này không thay thế kiểm thử Kafka thực tế.
Sau khi khởi động với cấu hình thực tế,
kiểm tra `GET http://localhost:8086/actuator/health` và các API có Bearer token.

Trang **Kết nối dịch vụ** trên frontend kiểm tra Notification bằng
`GET /api/v1/notifications/health-check` qua Gateway, có Bearer access token.
Gateway chuyển GET này tới `/actuator/health` của Notification. Trạng thái bao gồm
kết nối database; không xác nhận luồng Kafka/SSE từ đầu đến cuối. Có thể kiểm tra
lại trên UI khi service phục hồi; lỗi Notification không chặn kết quả service khác.

Nếu UI báo “Không thể kết nối dịch vụ”, kiểm tra trực tiếp
`http://localhost:8086/actuator/health`. HTTP `503` với `status: DOWN` nghĩa là
service đã phản hồi nhưng có thành phần chưa sẵn sàng; xem log để xác định lỗi.
Với lỗi `password authentication failed`, kiểm tra `SPRING_DATASOURCE_URL`,
`SPRING_DATASOURCE_USERNAME` và `SPRING_DATASOURCE_PASSWORD` trong môi trường chạy.
`.env.example` chỉ là mẫu; ngay cả khi đã có `.env`, Maven/IDE cũng không tự nạp.
Nạp file bằng `set -a; source ./.env; set +a` trước khi chạy Maven, hoặc cấu hình
các biến trong Run Configuration của IDE. Dùng đúng cổng PostgreSQL của máy
(`5432` hoặc `55432`), restart Notification, rồi bấm **Kiểm tra lại** trên UI.

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
5. Để nhận kết quả cấp tài khoản, bật `HRM_EVENTS_ENABLED=true` ở Auth/Employee,
   giữ `HRM_NOTIFICATIONS_ENABLED=true` ở Notification và bảo đảm topic
   `hrm.auth.account-results.v1` tồn tại (Kafka init hiện đã tạo topic này).
   Triển khai Employee nhận payload mở rộng và Auth phát `data.requestedBy` cùng
   request gốc; sau đó chạy Notification đã áp schema `002` và frontend mới.

Notification nghe kết quả Auth bằng group `hrm-notification-account-results-v1`,
độc lập với group của Employee nên cả hai đều nhận được kết quả. Auth bổ sung
`data.requestedBy` cho cả `AccountCreated` và `AccountCreationFailed` trong schema v1.
Sự kiện cũ thiếu trường này được bỏ qua riêng tại Notification vì không xác định
được người nhận; Employee vẫn xử lý chúng. Không gửi thông báo chung cho tất cả HR/Admin.

Producer ghi outbox cùng transaction nghiệp vụ rồi publish theo lịch. Consumer
dùng group `hrm-notifications-v1`, chống trùng theo `eventId`; lỗi được retry,
chưa có dead-letter topic. Notification không tự cập nhật coverage của Attendance.

## API

Tất cả API yêu cầu Bearer access JWT của Auth; prefix `/api/v1/notifications`.

| Method / đường dẫn                                       | Hành vi                                                             |
| -------------------------------------------------------- | ------------------------------------------------------------------- |
| `GET /api/v1/notifications?before=<cursor>`              | Trang tối đa 20 mục, trả `nextBefore` và `unreadCount`              |
| `PATCH /api/v1/notifications/{id}/read`                  | Đánh dấu một thông báo đã đọc, trả `204`                            |
| `POST /api/v1/notifications/read-all`                    | Body `{"through": <cursor>}`, đánh dấu đã đọc tới cursor, trả `204` |
| `GET /api/v1/notifications/stream?accountAfter=<cursor>` | SSE có xác thực; cursor kết quả account tùy chọn                    |

Thông báo được lọc theo người gửi đơn hoặc quyền duyệt HR/ADMIN; hành động của
chính người đang xem không được tính như thông báo cho họ. Trạng thái đã đọc
được lưu theo người dùng.

SSE gửi event `notifications` với `{revision, readCount, unreadCount}` khi snapshot
thay đổi, còn lại gửi heartbeat. Stream đóng khi JWT hết hạn hoặc sau khoảng
60 giây; client cần kết nối lại với Bearer token hợp lệ và gọi API danh sách để
lấy nội dung. Giới hạn 8 kết nối/người dùng và 500 kết nối/instance; vượt giới hạn
trả `429`. Frontend HR/ADMIN dùng stream này để cập nhật badge hàng chờ nghỉ phép
và đi trễ/về sớm; tự kết nối lại với Bearer token và dừng kết nối khi đăng xuất.

Cùng stream phát thêm event `account-results` cho HR/Admin đã gửi yêu cầu:

```json
{
    "cursor": 42,
    "items": [
        {
            "sequence": 42,
            "requestId": "uuid",
            "employeeId": "uuid",
            "provisioningStatus": "SUCCEEDED",
            "errorCode": null
        }
    ]
}
```

Mỗi batch tối đa 50 kết quả, theo thứ tự cursor tăng dần. Kết nối đầu tiên không
có `accountAfter` bắt đầu từ kết quả mới nhất và gửi cursor ban đầu, tránh hiện
lại toàn bộ lịch sử. Frontend lưu cursor theo user/tab và gửi lại khi reconnect;
server luôn lọc theo user trong JWT, không dùng cursor để xác định người nhận.

Frontend gọi API Employee để xác nhận trạng thái cuối cùng trước khi hiện toast,
vì consumer Employee có thể xử lý chậm hơn Notification. Yêu cầu đang chờ được
lưu theo user trong sessionStorage và poll mỗi 3 giây, sau 30 lần giảm còn mỗi
30 giây; vẫn theo dõi khi chuyển trang trong workspace. Kết quả hoàn tất được
ghi nhận theo requestId trước khi hiện toast để chống lặp do SSE/polling/reconnect.
Nếu sessionStorage bị chặn, theo dõi và chống trùng vẫn hoạt động trong bộ nhớ
cho đến khi tải lại trang. Tra cứu thủ công yêu cầu của người khác chỉ cập nhật
panel, không tạo toast cho người đang tra cứu.

## Phạm vi triển khai

`run-all.sh` đã chạy Notification. Hai bộ Docker Compose hiện chưa có service,
database, schema và biến đích Gateway cho Notification; cần bổ sung triển khai
trước khi bật tính năng trong Docker. Sự kiện Notification kích hoạt tải lại
`pending-count` từ Leave và Attendance; badge hiển thị số đơn chờ duyệt. Nếu
Notification chưa sẵn sàng, frontend vẫn cập nhật các số này mỗi 30 giây.

## Thông báo lỗi

Các thông báo lỗi do service khai báo được quản lý tại
[`ExceptionMessages.java`](src/main/java/com/company/notification/exception/ExceptionMessages.java).
Service, exception handler và lớp bảo mật tham chiếu các hằng số trong file này.
Thông báo có tham số dùng template `%s` và `.formatted(...)`.
Khi sửa nội dung API trả về, kiểm tra mapping lỗi và bản dịch phía frontend;
HTTP status và mã lỗi giao thức vẫn được quyết định tại nơi xử lý.
