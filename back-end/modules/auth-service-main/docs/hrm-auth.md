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

Tạo refresh session, refresh, logout và logout-all phối hợp bằng khóa ghi trên hàng User
trong cùng transaction, theo thứ tự User trước rồi refresh session. Nếu refresh lấy khóa trước,
logout-all chờ và thu hồi cả phiên mới; nếu logout-all lấy khóa trước, refresh chờ rồi bị từ chối.
Logout-all cập nhật trực tiếp các phiên chưa thu hồi của user đó. Login mới lấy khóa sau
logout-all vẫn có thể tạo phiên mới; logout-all không khóa tài khoản và không cấm đăng nhập lại.

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

### 2. Tạo Admin và dữ liệu demo

Chạy từ thư mục `auth-service-main`, sau bước tạo schema:

```sh
psql -X -v ON_ERROR_STOP=1 -h localhost -p 5432 -U auth_user -d auth_db -f docs/sql/002_create_admin.sql
psql -X -v ON_ERROR_STOP=1 -h localhost -p 5432 -U auth_user -d auth_db -f docs/sql/003_seed_demo_accounts.sql
```

Hai file seed là SQL PostgreSQL thông thường, có transaction; cũng có thể chạy toàn bộ file
trong SQL console của IDE đã kết nối `auth_db`. File `001` có lệnh riêng của `psql`, cần chạy
qua terminal. Nếu console đang ở transaction lỗi, chạy `ROLLBACK;` trước khi chạy seed.

Mật khẩu local của các tài khoản mới: **`Admin@123456`**, được lưu dưới dạng BCrypt cost 12.
Hash đã được tạo và xác minh bằng `BCryptPasswordEncoder` của service. Tất cả tài khoản mẫu
chỉ dùng cho phát triển local.

| File | Email đăng nhập | Employee | Roles | Active |
|---|---|---|---|---|
| `002` | `admin@company.com` | EMP005 | EMPLOYEE, ADMIN | true |
| `003` | `hr@company.com` | EMP001 | EMPLOYEE, HR | true |
| `003` | `manager@company.com` | EMP002 | EMPLOYEE, MANAGER | true |
| `003` | `employee@company.com` | EMP003 | EMPLOYEE | true |
| `003` | `disabled@company.com` | EMP006 | EMPLOYEE | false |

UUID có dạng `10000000-0000-0000-0000-00000000000N`, khớp chính xác với dữ liệu trong
`employee-service/src/main/resources/static/sql/seed_employee_data.sql`.
EMP004 được để chưa có tài khoản, phục vụ thử API `/register`. Quyền ADMIN trên EMP005
là quyền tài khoản mẫu, không suy ra từ chức danh Accountant. Email đăng nhập ở Auth
có thể khác email liên hệ trong hồ sơ Employee.

Seed dùng `INSERT ... RETURNING id` để gán role cho đúng tài khoản, không giả định ID
bắt đầu từ 1. Chạy lại cùng email và employeeId không tạo trùng, không reset mật khẩu,
không bật lại tài khoản hoặc khôi phục role đã bị thay đổi. Nếu email/employeeId thuộc
liên kết khác, script báo lỗi và rollback toàn bộ lượt seed; cần kiểm tra dữ liệu trước
khi thử lại, không tự gắn sang tài khoản khác. Vì vậy mật khẩu mẫu chỉ được bảo đảm
cho tài khoản vừa được tạo bởi script, không phải tài khoản có sẵn được bỏ qua.

Seed không tạo refresh session hoặc JWT. Chúng được sinh khi đăng nhập thật.
Auth dùng `users.is_active`, không có cột enum `account_status`. Không có foreign key
xuyên database. Để thử liên kết đầy đủ, tạo schema và seed Employee trước.

Các script Auth không ghi vào database Employee và chưa phát sự kiện đồng bộ.
`Employee.accountStatus` có thể vẫn là `NOT_CREATED`/`UNKNOWN` dù đã có Account;
cần cơ chế đồng bộ/đối soát riêng, không dùng trạng thái mẫu đó để kết luận Account chưa tồn tại.

### 3. Chạy ứng dụng

Tạo hai cặp khóa RSA theo [hướng dẫn asymmetric JWT](asymmetric-jwt.md), rồi chạy:

```sh
./mvnw spring-boot:run
```

Đăng nhập Admin qua Swagger hoặc gọi:

```sh
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@company.com","password":"Admin@123456"}'
```

Lấy `data.accessToken` để gọi Employee hoặc `/api/v1/auth/register` của Auth.
Đăng nhập `disabled@company.com` trả `403` vì tài khoản bị vô hiệu hóa.

Ứng dụng giữ `ddl-auto: validate`, nên script phải chạy trước khi khởi động; SQL không
tự chạy khi ứng dụng start. Không chạy `001` chỉ để thêm dữ liệu mẫu: nó xóa toàn bộ
`auth_db`. Sau khi reset database, đăng nhập lại; token cũ có thể mang User ID được cấp
lại. Với môi trường reset đã từng phát hành token, thay hai cặp khóa RSA và cập nhật
access public key tại Employee trước khi khởi động lại.

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
