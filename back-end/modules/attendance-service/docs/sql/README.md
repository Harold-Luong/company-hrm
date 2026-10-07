# Attendance database

PostgreSQL riêng `attendance_db`, JPA `ddl-auto=validate`, không query/FK chéo database.
Áp `001_attendance_schema.sql` trước khi khởi động Attendance, kể cả database trống
đã tạo cho scaffold trước đó.

```sql
CREATE USER attendance_user WITH PASSWORD 'attendance_password';
CREATE DATABASE attendance_db OWNER attendance_user;
```

Mật khẩu trên chỉ dùng local. Sau khi tạo database, chạy từ thư mục service:

```bash
psql -h localhost -U attendance_user -d attendance_db -v ON_ERROR_STOP=1 -f docs/sql/001_attendance_schema.sql
```

Schema gồm mẫu ca/phiên bản, quy tắc phân công, batch audit, hàng khóa điều phối,
ngày công snapshot, sự kiện và idempotency. Seed ca mặc định UUID
`00000000-0000-0000-0000-000000000001`, version 0, giờ `08:00–12:00, 13:30–17:30`;
khoảng cho phép ghi nhận `06:00–22:00` có thể chỉnh bởi HR. **Chưa seed phân công**:
HR phải chọn ngày hiệu lực/ngày trong tuần và preview/apply qua API/UI.

Compose mount file vào `docker-entrypoint-initdb.d`; chỉ tự chạy khi volume PostgreSQL
mới. Với volume đã có, áp SQL thủ công bằng user Attendance, không xóa volume:

```bash
# Chạy từ back-end/modules/infra/gateway khi attendance-db đã chạy.
docker compose exec -T attendance-db psql -U attendance_user -d attendance_db -v ON_ERROR_STOP=1 < ../../attendance-service/docs/sql/001_attendance_schema.sql
```

File dùng CREATE IF NOT EXISTS/seed có điều kiện để chạy lại mà không reset dữ liệu;
không phải công cụ tự sửa schema khác phiên bản. Thay đổi sau này cần migration mới
và backup theo quy trình triển khai. [Contract và hướng dẫn cấu hình](../../README.md).
