# Khởi tạo database và chạy Calendar Service

Hướng dẫn này dùng PostgreSQL local, chạy schema và seed trong query editor, sau đó khởi động service từ terminal.

## Bước 1: Chuẩn bị

Cần có:

- PostgreSQL đang chạy và công cụ mở query editor, ví dụ công cụ database trong IDE hoặc pgAdmin.
- Tài khoản PostgreSQL có quyền tạo user và database để thực hiện bước 2.
- JDK 21. Kiểm tra trong terminal bằng `java -version`.

Thông số dùng xuyên suốt hướng dẫn:

| Thông số         | Giá trị             |
| ---------------- | ------------------- |
| PostgreSQL host  | `localhost`         |
| PostgreSQL port  | `5432`              |
| Database         | `calendar_db`       |
| User của service | `calendar_user`     |
| Mật khẩu local   | `calendar_password` |
| Cổng HTTP        | `8083`              |

Nếu PostgreSQL của bạn dùng cổng hoặc tài khoản khác, dùng đúng thông số đó ở cả kết nối query editor và biến môi trường tại bước 5.

## Bước 2: Tạo user và database

Nếu đã có `calendar_db` và đăng nhập được bằng `calendar_user`, chuyển sang bước 3.

Mở kết nối quản trị PostgreSQL tới database `postgres`, bằng tài khoản quản trị của bạn. Chạy **riêng từng câu** sau trong query editor, bật autocommit:

```sql
CREATE USER calendar_user WITH PASSWORD 'calendar_password';
```

```sql
CREATE DATABASE calendar_db OWNER calendar_user;
```

Không bọc hai câu trên trong `BEGIN`/`COMMIT`: PostgreSQL không cho chạy `CREATE DATABASE` trong transaction. Nếu user đã tồn tại, chỉ chạy câu tạo database và dùng mật khẩu hiện tại của user đó.

## Bước 3: Tạo bảng

Tạo hoặc mở kết nối query editor với:

- Database: `calendar_db`.
- User: `calendar_user`.
- Password: mật khẩu của `calendar_user`.

Kiểm tra bạn đang ở đúng kết nối:

```sql
SELECT current_database(), current_user;
```

Kết quả cần là `calendar_db` và `calendar_user`.

Mở [001_calendar_schema.sql](001_calendar_schema.sql), chọn **toàn bộ nội dung file** rồi chạy như một script, bao gồm `BEGIN` và `COMMIT`.

Kiểm tra các bảng:

```sql
SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_name IN ('calendar_events', 'calendar_event_audit')
ORDER BY table_name;
```

Kết quả có hai bảng: `calendar_event_audit` và `calendar_events`. Chỉ chạy script tạo bảng một lần. Nếu hai bảng đã tồn tại đúng schema, chuyển sang bước 4.

## Bước 4: Nạp dữ liệu lịch

Trên cùng kết nối `calendar_db` bằng `calendar_user`, mở [002_calendar_seed.sql](002_calendar_seed.sql), chọn **toàn bộ nội dung file** rồi chạy như một script.

Kiểm tra dữ liệu:

```sql
SELECT type, status, COUNT(*) AS total
FROM calendar_events
WHERE created_by = 'seed:everrise-vn'
GROUP BY type, status
ORDER BY type;
```

Với DB vừa khởi tạo, kết quả là:

| type          | status    | total |
| ------------- | --------- | ----- |
| COMPANY_EVENT | PUBLISHED | 1     |
| HOLIDAY       | PUBLISHED | 20    |
| OTHER         | PUBLISHED | 5     |

Xem chi tiết:

```sql
SELECT id, title, description, type, holiday_kind, start_date, end_date, status
FROM calendar_events
ORDER BY start_date, id;
```

Seed tạo 26 sự kiện `PUBLISHED` và ghi lịch sử vào `calendar_event_audit`. Chạy lại không thêm sự kiện trùng tiêu đề, loại, loại nghỉ và khoảng ngày. Các bản ghi do seed tạo còn `DRAFT`, version 0, nội dung chưa chỉnh sửa được chuyển sang `PUBLISHED` và ghi audit; bản nháp do người dùng tạo hoặc đã chỉnh sửa không bị công bố.

## Bước 5: Khởi động service

Mở terminal Bash và chạy:

```bash
cd /home/duc-trong/company-hrm/back-end/modules/calendar-service

export CALENDAR_DB_URL='jdbc:postgresql://localhost:5432/calendar_db'
export CALENDAR_DB_USER='calendar_user'
export CALENDAR_DB_PASSWORD='calendar_password'
export CALENDAR_PORT=8083

./mvnw spring-boot:run
```

Dùng mật khẩu thực tế nếu khác ví dụ. Service sử dụng các bảng đã tạo ở bước 3. Hibernate kiểm tra schema khi khởi động, không tự tạo hoặc sửa bảng.

Các biến `export` chỉ áp dụng cho terminal này và các tiến trình được chạy từ nó. Nếu bấm Run trong IDE, đặt bốn biến tương ứng trong **Run Configuration → Environment variables**.

Lần chạy Maven Wrapper đầu tiên cần kết nối mạng để tải Maven và dependency. Giữ terminal chạy service mở; đợi log `Started CalendarServiceApplication` trước khi làm bước 6.

Service cần access public key của Auth. Đặt `JWT_ACCESS_PUBLIC_KEY` nếu key không nằm tại `file:../auth-service-main/keys/access-public.pem`; chỉ sử dụng public key cho access token.

## Bước 6: Kiểm tra service và Swagger UI

Mở trong trình duyệt:

- Health: [http://localhost:8083/actuator/health](http://localhost:8083/actuator/health) — cần trả `{"status":"UP"}`.
- Swagger UI: [http://localhost:8083/swagger-ui.html](http://localhost:8083/swagger-ui.html).
- API lịch: [http://localhost:8083/api/v1/calendar?year=2026](http://localhost:8083/api/v1/calendar?year=2026).

Trong Swagger, mở **Calendar → GET /api/v1/calendar → Try it out**, nhập `2026` vào `year` rồi chọn **Execute**. Trước khi thử, chọn **Authorize** và nhập access token lấy sau khi đăng nhập Auth. API yêu cầu header `Authorization: Bearer <access-token>`; mở link API trực tiếp mà không có token sẽ trả `401`.

Sau khi seed trên DB mới, API trả **13 sự kiện cho năm 2026** và **13 sự kiện cho năm 2027**. API dành cho nhân viên đã đăng nhập chỉ trả `PUBLISHED` hoặc `CANCELLED`; bản nháp `DRAFT` không hiển thị. Nếu danh sách rỗng, kiểm tra truy vấn tại bước 4 và kết nối DB của service.

## Khi gặp lỗi

| Lỗi                                                     | Cách xử lý                                                                                                                                        |
| ------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------- |
| `Connection refused` khi kết nối PostgreSQL             | Kiểm tra PostgreSQL đã chạy và đúng host/port. Cổng DB là `5432` theo hướng dẫn, cổng HTTP là `8083`.                                             |
| `password authentication failed`                        | Kiểm tra đăng nhập query editor bằng `calendar_user`; dùng cùng mật khẩu trong `CALENDAR_DB_PASSWORD`.                                            |
| `database "calendar_db" does not exist`                 | Tạo database ở bước 2 trên đúng PostgreSQL host/port đang dùng.                                                                                   |
| `CREATE DATABASE cannot run inside a transaction block` | Bật autocommit và chạy riêng câu `CREATE DATABASE`, ngoài transaction.                                                                            |
| `relation ... already exists`                           | Không chạy lại schema trên bảng đã có. Nếu phiên SQL đang lỗi, chạy `ROLLBACK;` trước; kiểm tra bảng rồi chuyển sang bước 4 nếu schema đã đầy đủ. |
| `current transaction is aborted`                        | Chạy `ROLLBACK;`, tìm lỗi đầu tiên trong output, sửa lỗi đó rồi chạy lại toàn bộ script.                                                          |
| `permission denied for table`                           | Kiểm tra bảng được tạo bằng `calendar_user` ở bước 3. Nếu bảng thuộc tài khoản khác, chủ bảng cần cấp quyền phù hợp cho `calendar_user`.          |
| `Schema-validation: missing table`                      | Kiểm tra `CALENDAR_DB_URL` trỏ tới đúng DB đã chạy schema và script đã hoàn tất `COMMIT`.                                                         |
| Cổng `8083` đang được dùng                              | Dừng service đang chiếm cổng hoặc đổi `CALENDAR_PORT`, rồi mở URL theo cổng mới.                                                                  |
| API trả `200` nhưng `events` rỗng                       | Kiểm tra năm truy vấn và trạng thái sự kiện; `DRAFT` không được trả qua API dành cho nhân viên đã đăng nhập.                                                            |
