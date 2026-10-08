# Chạy HRM bằng Docker

Bộ [compose.yaml](compose.yaml) tại thư mục gốc dành cho phát triển local, gồm
frontend Vue/Nginx, API Gateway, Auth, Employee, Calendar, Leave, Attendance,
5 PostgreSQL database riêng và Kafka. Không cần cài Java, Maven, Node hoặc
PostgreSQL trên máy host.

## Khởi động

Mở Docker Desktop, dùng **Linux containers** và đợi Docker Engine sẵn sàng.
Cần Docker Compose **2.24 trở lên** (hỗ trợ `!reset`); kiểm tra bằng
`docker version` và `docker compose version`. Nên cấp khoảng 8 GB RAM cho Docker
để chạy đồng thời các JVM, PostgreSQL và Kafka.

Tại thư mục gốc repository:

```powershell
docker compose config --quiet
docker compose up -d --build --wait --wait-timeout 600
docker compose ps
```

Lần đầu cần Internet để tải base images, Maven dependencies và npm packages.
Compose tự tạo hai cặp RSA JWT, tạo schema và nạp seed demo trên các volume mới.
`keys-init` và `kafka-init` kết thúc với mã `0` là bình thường; các service ứng dụng
đợi khóa, database, Kafka topics và backend phụ thuộc sẵn sàng.

- Giao diện: **http://localhost:5173**.
- Gateway health: **http://localhost:8080/actuator/health**.
- Tài khoản demo: **admin@company.com**, mật khẩu **Admin@123456**.
- Có thể thử `hr@company.com`, `manager@company.com`, `employee@company.com`
  với cùng mật khẩu. Dữ liệu này lấy từ seed Auth/Employee hiện có.

Chỉ frontend và Gateway publish cổng, bind vào `127.0.0.1`. Database và Kafka
không chiếm cổng PostgreSQL/Kafka trên máy host. Frontend gửi `/api/...` cùng
origin qua Nginx tới Gateway; route Vue vẫn mở được khi tải lại trang.

## Cấu hình tùy chọn

Không bắt buộc tạo `.env`. Nếu cần đổi cổng hoặc cấu hình email, sao chép
[.env.example](.env.example) thành `.env` (không ghi đè nếu đã có):

```powershell
Copy-Item .env.example .env
# Ví dụ sửa FRONTEND_PORT=3000, GATEWAY_PORT=8088 rồi chạy lại:
docker compose up -d --build --wait --wait-timeout 600
```

Trên Bash dùng `cp .env.example .env`. Frontend URL, CORS và URL trang kích hoạt
tự dùng `FRONTEND_PORT`. `GATEWAY_PORT` chỉ thay cổng host; Nginx vẫn gọi cổng
8080 trong Docker.

Email kích hoạt mặc định tắt. Muốn thử luồng gửi mail, đặt
`AUTH_ACTIVATION_ENABLED=true`, `AUTH_ACTIVATION_TOKEN_SECRET`, `RESEND_FROM`
và `RESEND_API_KEY` theo [hướng dẫn Auth](back-end/modules/auth-service-main/docs/ACCOUNT-ACTIVATION.md).
Các tài khoản seed đã kích hoạt, không cần mail để đăng nhập. Tài khoản cấp mới
cần luồng kích hoạt; cấp tài khoản thành công không đồng nghĩa đăng nhập được ngay.

## Attendance trong bộ local

Gateway chuyển tiếp `/api/v1/attendance/**` tới Attendance và yêu cầu access JWT.
Attendance gọi Employee, Calendar và Leave qua DNS nội bộ của Compose.

Network local dùng `172.30.80.0/24`, Nginx tại `172.30.80.10`, Gateway tại
`172.30.80.20`. Các container còn lại nhận IP động trong `172.30.80.128/25`,
tách khỏi hai IP tĩnh để tránh lỗi `Address already in use` khi khởi động.
Mặc định chỉ tin Gateway và cho chấm công qua Nginx local;
Gateway nhìn thấy IP của Nginx, **không phải IP thực của trình duyệt**. Đây là
cấu hình demo trên máy cá nhân, không dùng để xác thực mạng công ty trong production.
Gọi check-in trực tiếp qua cổng 8080 có thể bị từ chối do khác IP.
Đặt `ATTENDANCE_ALLOWED_NETWORKS=` để từ chối toàn bộ check-in/out.

Đăng nhập HR/Admin để tạo/phân công ca trước khi thử chấm công bằng tài khoản
nhân viên. Ca mặc định trong schema chưa được tự gán cho nhân viên.
Nếu subnet trùng VPN/network đã có, thay subnet và hai địa chỉ tĩnh trong
`compose.yaml`, đồng thời cập nhật allowlist và trusted proxy tương ứng.

## Log, cập nhật và dữ liệu

```powershell
docker compose logs --tail=100 gateway auth employee calendar leave attendance
docker compose logs -f attendance
docker compose up -d --build --wait --wait-timeout 600
docker compose stop
docker compose start
docker compose down
```

`down` giữ dữ liệu. Các volume có prefix `company-hrm_`, tách biệt bộ HTTPS
`hrm` cũ và database local. Khóa JWT giữ nguyên qua restart/rebuild; chỉ Auth
mount volume private key, các service khác chỉ nhận public keys.

Script SQL chỉ chạy khi database volume trống. Thay schema hoặc mật khẩu trong
`.env` không tự cập nhật database đã có: áp migration/đổi mật khẩu DB phù hợp
trước khi chạy bản mới. Sao lưu cả dữ liệu và volume khóa khi cần giữ phiên đăng nhập.

Chỉ khi muốn **xóa toàn bộ dữ liệu demo và khóa JWT** để khởi tạo lại:

```powershell
docker compose down --volumes
docker compose up -d --build --wait --wait-timeout 600
```

Nếu service unhealthy, xem log service và database tương ứng. Lỗi kết nối Docker
Engine cần khởi động Docker Desktop trước. Nếu cổng 5173/8080 đã được dùng, đổi
cổng qua `.env` như trên.

Nếu đang dùng network từ cấu hình cũ chưa có `ip_range`, cần tạo lại network
để áp dụng dải IP động mới. Chạy hai lệnh sau, giữ nguyên các volume dữ liệu:

```powershell
docker compose down
docker compose up -d --wait --wait-timeout 600
```

Không thêm `--volumes` khi sửa lỗi mạng. Không cần build lại image cho thay đổi này.

## Bộ backend HTTPS hiện có

[back-end/modules/infra/gateway](back-end/modules/infra/gateway/README.md) là cấu
hình riêng cho Gateway HTTPS, secret files và database networks riêng; đã bổ sung
Attendance cùng database. Bộ đó không tự nạp tài khoản demo, không build frontend,
và yêu cầu chứng chỉ TLS, mật khẩu DB, khóa JWT và cấu hình mạng công ty riêng.
