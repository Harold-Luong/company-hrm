# Auth cho Attendance MVP

Module giữ JWT access token và refresh session hiện có. User chứa thông tin tài khoản;
User lưu `employee_id` bắt buộc/unique để liên kết một-một với Employee, kể cả tài khoản HR/ADMIN. Không đưa mã nhân viên,
phòng ban, trạng thái lao động hoặc ngày vào/nghỉ việc vào User.

## Role và field

| Role | Ý nghĩa |
| --- | --- |
| EMPLOYEE | Tài khoản nhân viên; vẫn cần Employee liên kết và đang active để chấm công |
| MANAGER | Quản lý; phạm vi team phải được module nghiệp vụ kiểm tra |
| HR | Quản trị nghiệp vụ nhân sự; được tạo tài khoản qua /register |
| ADMIN | Quản trị tài khoản; không mặc định có toàn bộ quyền HR |

Mỗi User có tập `roles` (`Set<UserRole>`), được lưu trong bảng `user_roles`.
Một account có thể có nhiều role, ví dụ `["EMPLOYEE", "HR"]`; role không trùng và không có thứ tự.
Không có role hierarchy tự động. Các API tự phục vụ trong module Employee/Attendance sau này
cần kiểm tra Employee liên kết và phạm vi dữ liệu, không chỉ `hasRole('EMPLOYEE')` vì Manager/HR
cũng có thể là nhân viên. Việc khai báo role chưa triển khai quyền nghiệp vụ của các module đó.

User có `active`, password hash, `createdAt`, `updatedAt` và `lastLoginAt` nullable:
chỉ cập nhật khi đăng nhập thành công, trong cùng transaction tạo refresh session. Login thất bại
hoặc refresh không cập nhật. `/me` trả các timestamp này, không trả password hash.
Trạng thái account `active` độc lập với trạng thái lao động của Employee.

## API

`POST /api/v1/auth/register` yêu cầu access token của tài khoản có HR hoặc ADMIN, được kiểm tra ở HTTP và service
(`@PreAuthorize`). Tham khảo [Spring method security](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html).
Request gồm `email`, `password`, `employeeId` bắt buộc và `roles` tùy chọn dưới dạng mảng. Bỏ qua/null `roles` sẽ dùng `["EMPLOYEE"]`.
`employeeId` là chuỗi UUID (ví dụ `550e8400-e29b-41d4-a716-000000001001`); thiếu/null/sai định dạng trả 400. Nhân viên đã có tài khoản trả 400.
Cột `users.employee_id` có kiểu PostgreSQL `uuid`. Database áp dụng NOT NULL và UNIQUE cho `users.employee_id`. Đây là ID tham chiếu tới Employee;
auth chưa có module Employee để kiểm tra nhân viên tồn tại hoặc đang active và chưa có foreign key liên dịch vụ.
Mảng rỗng, phần tử null, role không hợp lệ hoặc truyền chuỗi thay mảng trả 400; role trùng được gộp.
Chỉ chấp nhận bốn role: EMPLOYEE, MANAGER, HR, ADMIN. Không truyền token trả 401;
không có HR/ADMIN hoặc account inactive trả 403; role không hợp lệ trả 400.

Đăng nhập tài khoản HR hoặc Admin trước khi tạo tài khoản từ UI/Swagger. Không có đăng ký công khai.
Chưa có API thay đổi role, khóa account hoặc quản trị Employee.

Access JWT có claim `roles` dạng mảng chuỗi, ví dụ `["EMPLOYEE", "HR"]`.
`/me` cũng trả `roles` dạng mảng; không còn field `role`.
Access JWT có claim `employee_id` dạng chuỗi UUID; `/me` trả `employeeId` dạng chuỗi UUID từ database. JWT `sub` vẫn là User ID.
Login/refresh lấy liên kết nhân viên hiện tại; JWT đã cấp không tự đổi payload khi liên kết thay đổi.
JWT filter xác minh token rồi đọc tập roles và trạng thái account
hiện tại trong DB để tạo các Spring Security authority. Vì vậy token ADMIN cũ không giữ được quyền
sau khi đổi role; account inactive không sử dụng được access/refresh token. Account đã xóa trả 401.
Thay đổi role không sửa payload của JWT đã phát hành; frontend đọc `/me` để lấy role hiện tại,
token cấp sau login/refresh mang role mới. Mỗi request xác thực cần đọc account và tập roles từ DB.

Logout vẫn chỉ thu hồi refresh session. Khi account active trở lại, access token chưa hết hạn
có thể dùng lại; việc thu hồi vĩnh viễn access token không thuộc thay đổi này.

## Khởi tạo database từ đầu

Auth được thiết lập như project mới, không có migration hoặc lớp tương thích dữ liệu cũ.
Hibernate dùng `ddl-auto=validate`; SQL là nguồn khởi tạo schema, ứng dụng không tự tạo bảng.
Các file SQL nằm trong `docs/sql`, không nằm trong thư mục web `static`.

### 1. Tạo lại database local

Dừng ứng dụng và đóng các kết nối tới `auth_db`. Từ thư mục module, chạy bằng PostgreSQL
superuser trên server local:

```sh
psql -X -h localhost -p 5432 -U postgres -d postgres -f docs/sql/001_init_auth_db.sql
```

Script **xóa toàn bộ database `auth_db` rồi tạo mới**, bao gồm tài khoản và refresh session.
Không dùng `FORCE`; nếu còn kết nối thì lệnh dừng ở bước drop. `DROP/CREATE DATABASE` không
chạy trong transaction; phần tạo bảng/index chạy trong một transaction sau khi kết nối database mới.

Script tạo PostgreSQL login `auth_user` nếu chưa tồn tại, với mật khẩu local `auth_password`
khớp `application.yaml`. Nếu login đã tồn tại thì giữ nguyên mật khẩu và quyền của login đó;
cấu hình datasource phải dùng đúng credential hiện tại. Không xóa cluster-wide role hoặc database khác.

Schema gồm:

* `users`: email unique, `employee_id` bắt buộc/unique, password hash, active, `last_login_at`, audit timestamps.
* `user_roles`: `user_id`, `role`; khóa chính ghép ngăn role trùng trên cùng account.
* `refresh_sessions`: UUID session, foreign key tới User, token hash, thời gian tạo/hết hạn/thu hồi.
* Check constraint chỉ chấp nhận EMPLOYEE, MANAGER, HR, ADMIN; index session theo `user_id`.

Database khởi tạo rỗng, không có tài khoản demo hoặc ID seed cố định. Chạy lại script sẽ reset
dữ liệu và identity sequence về trạng thái mới. Script dành cho môi trường phát triển local.

### 2. Tạo Admin đầu tiên

`002_create_admin.sql` là một câu `INSERT` PostgreSQL thông thường. Trong SQL console của IDE,
chọn database `auth_db`, mở file và chạy toàn bộ câu lệnh. Nếu auto-commit đang tắt thì commit
sau khi insert thành công.

Tài khoản mẫu cho local:

* Email: `admin@company.com`
* Mật khẩu: `Admin@123456`
* Role: `ADMIN`

Các UUID nhân viên trong file là dữ liệu mẫu; thay bằng UUID tương ứng từ Employee.
File lưu BCrypt hash cost 12 của mật khẩu này. Để tạo tài khoản khác, thay email và password hash;
tạo hash bằng `new BCryptPasswordEncoder(12).encode(password)`. Thay thông tin mẫu trước khi dùng
ngoài môi trường local. ID do database sinh, `last_login_at` là null cho tới lần đăng nhập đầu tiên.

Hoặc chạy qua terminal:

```sh
psql -X -v ON_ERROR_STOP=1 -h localhost -p 5432 -U auth_user -d auth_db -f docs/sql/002_create_admin.sql
```

Chạy một lần để tạo Admin; chạy lại cùng email sẽ báo lỗi unique constraint, không sửa account
đã tồn tại. Nếu console báo `current transaction is aborted` do lần chạy lỗi trước, chạy
`ROLLBACK;` rồi chạy lại câu insert. Không cần chạy lại script reset database để tạo Admin.
File `001_init_auth_db.sql` vẫn dùng terminal `psql` như bước 1.

### 3. Chạy ứng dụng

Cấu hình `JWT_SECRET_ACCESS`, `JWT_SECRET_REFRESH`, chạy Spring Boot, đăng nhập Admin và gọi
`/api/v1/auth/register` để tạo các tài khoản còn lại. Sau khi reset database, cần đăng nhập lại;
token đã phát hành không nên tái sử dụng vì User ID có thể được cấp lại. Với môi trường reset
đã từng phát hành token, thay cả hai JWT signing secret trước khi khởi động lại ứng dụng.

## Kiểm thử

`mvn test` kiểm tra một/nhiều role qua register/login/refresh/me, mặc định EMPLOYEE, role trùng,
từ chối tập rỗng/null element/role ngoài enum, quyền HR hoặc ADMIN ở HTTP/service,
token cũ sau đổi role, account inactive/deleted và thời điểm
đăng nhập; kiểm tra employeeId bắt buộc/không trùng và truyền qua register/login/me/refresh.
Các regression test validation, throttling, logout và refresh rotation vẫn được chạy.
Các API test dùng H2; SQL khởi tạo/reset cần được kiểm tra riêng trên PostgreSQL tạm.

Nếu môi trường chặn Mockito tự attach agent, chạy với agent có sẵn trong Maven cache:

```sh
mvn test -DargLine="-javaagent:/path/to/mockito-core-5.23.0.jar"
```
