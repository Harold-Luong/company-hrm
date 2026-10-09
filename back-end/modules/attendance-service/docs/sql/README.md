# Attendance database

Attendance dùng PostgreSQL riêng `attendance_db`, JPA `ddl-auto=validate`, không
query/FK chéo database. **Chỉ có một file khởi tạo:**
[001_attendance_schema.sql](001_attendance_schema.sql).

Trong giai đoạn phát triển, sửa cấu trúc trực tiếp trong file này rồi tạo lại
database. Không cần chạy các file migration theo thứ tự. File dùng `CREATE IF NOT
EXISTS` để có thể chạy lại trên cùng schema và giữ seed duy nhất; nó không chuyển
đổi cấu trúc database cũ.

## Tạo mới

Tạo user/database bằng tài khoản quản trị PostgreSQL (mật khẩu ví dụ chỉ dùng local):

```sql
CREATE USER attendance_user WITH PASSWORD 'attendance_password';
CREATE DATABASE attendance_db OWNER attendance_user;
```

Nếu đã có user thì chỉ tạo database. Từ thư mục `attendance-service`, chạy:

```bash
psql -h localhost -U attendance_user -d attendance_db -v ON_ERROR_STOP=1 -f docs/sql/001_attendance_schema.sql
```

Có thể mở file trong DBeaver, chọn kết nối `attendance_db` bằng `attendance_user`
và chạy **toàn bộ script**, gồm cả `BEGIN`, function/trigger và `COMMIT`.

## Tạo lại database khi phát triển

Dừng Attendance, đóng các kết nối tới `attendance_db`. Trên kết nối quản trị tới
`postgres`, chạy từng lệnh dưới đây ngoài transaction. Thao tác này xóa toàn bộ
dữ liệu Attendance; nếu cần giữ dữ liệu thì xuất bản sao lưu trước.

```sql
DROP DATABASE IF EXISTS attendance_db;
CREATE DATABASE attendance_db OWNER attendance_user;
```

Sau đó chạy lại **duy nhất** `001_attendance_schema.sql` bằng lệnh phía trên và
khởi động backend. Script schema không tự xóa database.

Compose chỉ mount file này vào `docker-entrypoint-initdb.d`, tự chạy với volume
PostgreSQL mới. Với database trống đã tạo lại trong Compose, từ `infra/gateway`:

```bash
docker compose exec -T attendance-db psql -U attendance_user -d attendance_db -v ON_ERROR_STOP=1 < ../../attendance-service/docs/sql/001_attendance_schema.sql
```

## Nội dung schema

- 13 bảng cho mẫu ca/phiên bản, phân công, ngày công, đơn đi trễ/về sớm, OT,
  đơn bổ sung/điều chỉnh, lịch sử và idempotency.
- FK `(shift_id, shift_version)` từ ngày công, phân công và đơn tới revision;
  không lưu JSON ca lặp trong các bảng này. Trigger chặn UPDATE revision, FK
  ngăn xóa revision đang được sử dụng.
- Unique nhân viên/ngày, ngày công/loại sự kiện và actor/type/idempotency key;
  index phục vụ báo cáo theo khoảng ngày, lịch sử và kiểm tra OT trùng giờ.
- `response_body` của operations cho phép null để dọn payload vào/ra hết hạn;
  khóa chống trùng vẫn được giữ. Cấu hình job trong README service.
- Seed hàng điều phối và mẫu ca UUID `00000000-0000-0000-0000-000000000001`,
  giờ `08:00–12:00, 13:30–17:30`, cửa sổ chấm công `06:00–22:00`.
  Khi backend khởi động, `DefaultScheduleInitializer` áp lịch thứ Hai–thứ Sáu
  từ ngày khởi tạo, không ngày kết thúc, nếu chưa có lịch mặc định công ty.

## Kiểm thử

`./mvnw test` đọc schema chính; test H2 bỏ khối được đánh dấu `POSTGRESQL ONLY`
cho partial index và trigger, không duy trì thêm file schema SQL riêng.

Kiểm tra đầy đủ trên PostgreSQL, dùng một schema thử riêng và tự xóa sau khi chạy:

```bash
# Cấu hình PGHOST, PGPORT, PGUSER, PGDATABASE; xác thực qua PGPASSWORD hoặc .pgpass.
scripts/verify-schema.sh
```

Script kiểm tra tạo đủ bảng, seed không trùng khi chạy lại, FK, revision bất biến
và unique sự kiện chấm công. [Contract và cấu hình service](../../README.md).
