# Auth cho HRM

Module giữ JWT access token và refresh session hiện có. User chứa thông tin tài khoản;
User lưu `employee_id` bắt buộc/unique để liên kết một-một với Employee, kể cả tài khoản HR/ADMIN. Không đưa mã nhân viên,
phòng ban, trạng thái lao động hoặc ngày vào/nghỉ việc vào User.

## Thứ tự chạy local

Từ thư mục gốc repository, vào `back-end/modules/auth-service-main`; dùng JDK 21
và PostgreSQL. Làm theo thứ tự:

1. Chuẩn bị role `auth_user` và database `auth_db` nếu chưa có.
2. Chạy [001_auth_schema.sql](sql/001_auth_schema.sql) để tạo schema đầy đủ.
3. Chạy [002_auth_seed.sql](sql/002_auth_seed.sql) nếu cần tài khoản local để đăng nhập.
4. Tạo RSA keys một lần theo [hướng dẫn JWT](asymmetric-jwt.md#tạo-khóa-cho-local).
5. Chuẩn bị/nạp `.env` rồi khởi động Auth theo [phần chạy Auth](#3-chạy-auth).

Lệnh SQL và danh sách tài khoản nằm ở [phần khởi tạo database và seed](#khởi-tạo-database-và-seed).
Không cần chạy thêm script migration Auth rời cho Kafka hoặc email: schema mới đã
bao gồm cả hai. Seed không gửi mail hoặc tạo phiên đăng nhập.

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

User có `active`, `activationPending`, password hash, `createdAt`, `updatedAt` và `lastLoginAt` nullable:
chỉ cập nhật khi đăng nhập thành công, trong cùng transaction tạo refresh session. Login thất bại
hoặc refresh không cập nhật. `/me` trả các timestamp này, không trả password hash.
Trạng thái account `active` độc lập với trạng thái lao động của Employee.

## API hiện có

Phần `/register` dưới đây áp dụng khi `HRM_EVENTS_ENABLED=false` (mặc định).
Khi bật cờ, API này trả `409` và UI gửi yêu cầu qua Employee; xem phần Kafka bên dưới.

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

## Cấp tài khoản qua Employee

```text
Employee xác minh nhân viên và quyền yêu cầu
    → EmployeeAccountRequested → Kafka → Auth tạo Account(employeeId)
Auth → AccountCreated / AccountCreationFailed → Kafka → Employee theo dõi kết quả
```

UI tạo hồ sơ trước rồi yêu cầu cấp tài khoản tại Employee. Auth nhận yêu cầu từ
producer nội bộ được xác thực, không cần đợi `EmployeeCreated`, bảng
`employee_references` hoặc gọi lại Employee chỉ để kiểm tra ID.
`EMPLOYEE_NOT_SYNCED` không còn là lỗi của flow này.

Auth kiểm tra payload, chính sách role và unique employee/email. MVP cấp role
`EMPLOYEE` theo chính sách Auth, không tin role tùy ý do client gửi. `requestedBy`
dùng audit, không thay thế xác thực producer/ACL. Consumer gọi service nghiệp vụ
nội bộ, không gọi HTTP `/register`.

Phần đã triển khai (cần schema Auth đầy đủ và bật `HRM_EVENTS_ENABLED=true`):

- Consumer `EmployeeAccountRequested` trên `hrm.employee.account-requests.v1`,
  group `auth-account-requests-v1`; Compose đã tạo topic yêu cầu/kết quả.
- Chống trùng `eventId`/`requestId`, lưu kết quả yêu cầu. Cùng yêu cầu giao lại dùng
  kết quả đã lưu; yêu cầu mới cho nhân viên đã có Account trả
  `ACCOUNT_ALREADY_EXISTS`, không tự mở khóa hoặc gắn sang Account khác.
- Transaction Account + kết quả + Outbox `AccountCreated`; từ chối nghiệp vụ ghi
  `AccountCreationFailed` với mã như `EMAIL_ALREADY_USED`. Lỗi hạ tầng rollback/retry.
- Kết quả gửi qua `hrm.auth.account-results.v1`: `eventId` mới, cùng `requestId`,
  `employeeId`, `correlationId`. Thành công có `accountStatus` và `accountVersion`,
  không cần trả `accountId` cho Employee.
- Account ban đầu chờ kích hoạt, chưa được đăng nhập; Auth quản lý lời mời và
  thiết lập mật khẩu. Không gửi mật khẩu/hash/token kích hoạt trong message.
  Đã bổ sung `activation_pending`, phân biệt chờ kích hoạt với bị khóa. Account
  có hash từ bí mật ngẫu nhiên riêng không được giữ lại/phát ra ngoài, không có
  mật khẩu chung. Đã có [email kích hoạt qua Resend](ACCOUNT-ACTIVATION.md), token một lần
  có hạn dùng, API đặt mật khẩu và API HR/Admin gửi lại lời mời.

Kích hoạt đã phát `AccountStatusChanged` và Employee cập nhật theo version.
Còn cần triển khai: API khóa/mở và sự kiện tương ứng, DLT, đối soát và vận hành production.

Employee theo dõi `PENDING/SUCCEEDED/FAILED` riêng với bản sao `accountStatus`.
Auth không cập nhật trạng thái lao động `EmployeeStatus`.

Khi bật sự kiện, `/register` cũ trả `409` để không tạo Account thiếu Outbox.
Khi cờ tắt (mặc định), API cũ vẫn dùng được như mô tả ở trên. Login/refresh/filter
đều từ chối Account chờ kích hoạt. Auth cần schema đầy đủ trong `docs/sql/001_auth_schema.sql` trước khi start,
kể cả khi chưa bật cờ sự kiện hoặc email.
Xem [hướng dẫn chạy](../../infra/kafka/ACCOUNT-PROVISIONING.md) và
[hợp đồng/các phase](../../EVENT-DRIVEN-GUIDE.md).

## Khởi tạo database và seed

Auth có đúng hai script trong `docs/sql`:

- [001_auth_schema.sql](sql/001_auth_schema.sql): schema đầy đủ, bao gồm tài khoản, phiên đăng nhập,
  provisioning Kafka, activation token và hàng đợi email.
- [002_auth_seed.sql](sql/002_auth_seed.sql): dữ liệu local có đủ bốn vai trò và account bị khóa.

Cả hai là SQL PostgreSQL, có transaction, chạy được bằng `psql` hoặc SQL console
của IDE. Không tự DROP database, không xóa dữ liệu, không tạo tài khoản demo trong
script schema. `ddl-auto=validate` được giữ nguyên; ứng dụng không tự chạy SQL.

### 1. Chuẩn bị database mới

Nếu chưa có role/database, mở một phiên `psql` bằng administrator:

```bash
psql -X -v ON_ERROR_STOP=1 -h localhost -U postgres -d postgres
```

Trong phiên này, chạy từng lệnh sau một lần:

```sql
CREATE ROLE auth_user LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION
    PASSWORD 'auth_password';
CREATE DATABASE auth_db OWNER auth_user;
```

Thoát phiên bằng `\q` trước khi chạy lệnh terminal bên dưới.
`CREATE DATABASE` phải chạy ngoài transaction. Nếu role/database đã tồn tại thì
bỏ qua lệnh tương ứng, không drop hoặc đổi mật khẩu role đang được sử dụng.
`auth_password` chỉ là cấu hình local mặc định của repository.

Từ thư mục gốc repository, chạy:

```bash
cd back-end/modules/auth-service-main
psql -X -v ON_ERROR_STOP=1 -h localhost -U auth_user -d auth_db -f docs/sql/001_auth_schema.sql
psql -X -v ON_ERROR_STOP=1 -h localhost -U auth_user -d auth_db -f docs/sql/002_auth_seed.sql
```

Schema có 8 bảng:

| Bảng | Chức năng |
|---|---|
| `users` | Email/employee_id unique, hash mật khẩu, active/activation_pending, thời gian audit |
| `user_roles` | EMPLOYEE, MANAGER, HR, ADMIN; khóa ghép chống vai trò trùng |
| `refresh_sessions` | Phiên đăng nhập, hash token, hết hạn/thu hồi |
| `account_provisioning_results` | Kết quả Kafka theo request/event, chống xử lý trùng |
| `account_link_versions` | Phiên bản trạng thái account theo Employee |
| `event_outbox` | Sự kiện Kafka với retry và lease |
| `account_activation_tokens` | Hash token, hạn dùng, đã dùng/thu hồi |
| `activation_mail_outbox` | Email activation, trạng thái gửi, retry và lease |

Không có bảng Employee hoặc foreign key xuyên database.
Các index hỗ trợ lookup phiên, lời mời và xử lý outbox; constraint cấm account vừa
active vừa chờ kích hoạt, role không hợp lệ, trạng thái email không hợp lệ.

Schema có thể chạy lại trên schema cùng phiên bản nhờ `IF NOT EXISTS`, nhưng
**không phải migration cho database cũ bị thiếu cột/constraint**: nó không tự ALTER
bảng đã tồn tại. Nếu cần giữ dữ liệu cũ, đối chiếu schema và thực hiện migration
riêng; không chạy reset database chỉ để thêm seed.

### 2. Tài khoản seed

Mật khẩu của tất cả account **mới được seed**: **`Admin@123456`** (BCrypt cost 12).
Chỉ dùng các account này trong môi trường phát triển.

| Email đăng nhập | Employee | Roles | Active |
|---|---|---|---|
| `admin@company.com` | EMP005 | EMPLOYEE, ADMIN | true |
| `hr@company.com` | EMP001 | EMPLOYEE, HR | true |
| `manager@company.com` | EMP002 | EMPLOYEE, MANAGER | true |
| `employee@company.com` | EMP003 | EMPLOYEE | true |
| `disabled@company.com` | EMP006 | EMPLOYEE | false |

`activation_pending=false` cho các account seed. Account disabled dùng để kiểm tra
login bị từ chối, không phải account chờ kích hoạt. Các UUID
`10000000-0000-0000-0000-00000000000N` khớp seed Employee; EMP004 được để trống để
thử tạo account qua Kafka và email thật. Seed không xếp hàng gửi mail hoặc tạo JWT.

Chạy lại seed không reset password, không thay đổi role/trạng thái hoặc tăng version
của account hiện có. Nếu email và employeeId xung đột với mapping khác, toàn bộ lượt
seed rollback. Role và version 1 chỉ được thêm cho account mới, không giả định user
ID bắt đầu từ 1. Account cũ có mật khẩu khác vẫn giữ mật khẩu đó.

`auth_demo_seed` trong script là bảng tạm `ON COMMIT DROP`, chỉ dùng để kiểm tra
mapping và gán role; không phải bảng nghiệp vụ thứ chín của Auth. Trong SQL console,
chạy toàn bộ script cùng một connection; không chạy từng đoạn INSERT rời khỏi
transaction. Nếu lượt chạy trước bị lỗi, chạy `ROLLBACK;` trước khi thử lại.

Seed không ghi vào database Employee, không phát sự kiện đồng bộ;
`Employee.accountStatus` của dữ liệu demo có thể chưa phản ánh account seed trong
Auth. Email đăng nhập cũng có thể khác email hồ sơ nhân viên.

### 3. Chạy Auth

Sau khi có schema và RSA keys, chạy trong thư mục module. Chỉ tạo `.env` nếu chưa
có; mở file và chọn chế độ chạy trước khi nạp biến môi trường:

```bash
test -f .env || cp .env.example .env
```

| Chế độ | `HRM_EVENTS_ENABLED` | `AUTH_ACTIVATION_ENABLED` | Điều kiện |
|---|---|---|---|
| Chỉ thử đăng nhập/refresh với account seed | `false` | `false` | PostgreSQL, schema, RSA keys |
| Cấp account qua Kafka, chưa gửi email | `true` | `false` | Thêm Kafka/topics và Employee đã cấu hình |
| Cấp account và gửi lời mời kích hoạt | `true` | `true` | Thêm cấu hình Resend, token secret và URL kích hoạt |

Sau khi lưu `.env`:

```bash
set -a
. ./.env
set +a
./mvnw spring-boot:run
```

Spring Boot không tự nạp `.env`. Với IDE, đặt working directory là module Auth và
nạp file env hoặc khai báo biến trong Run Configuration. Thay đổi `.env` cần nạp
lại và restart tiến trình. Thư mục chạy phải chứa `keys/` nếu dùng đường dẫn JWT mặc định.

Ở terminal khác, kiểm tra:

```bash
curl --fail http://localhost:8080/api/v1/auth/health-check
```

Kỳ vọng `status=UP`, `database=UP`; đây không phải kiểm tra Kafka/Resend. Mở Swagger
ở `http://localhost:8080/swagger-ui.html` để thao tác API.

Cấu hình mail/worker xem [ACCOUNT-ACTIVATION.md](ACCOUNT-ACTIVATION.md). Đăng nhập:

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@company.com","password":"Admin@123456"}'
```

Account `disabled@company.com` trả `403`. ADMIN không tự có quyền HR; cả hai vai
trò đều được phép gọi các API cấp tài khoản theo chính sách hiện tại.

## Kiểm thử

`./mvnw test` kiểm tra một/nhiều role qua register/login/refresh/me, mặc định EMPLOYEE, role trùng,
từ chối tập rỗng/null element/role ngoài enum, quyền HR hoặc ADMIN ở HTTP/service,
token cũ sau đổi role, account inactive/deleted và thời điểm
đăng nhập; kiểm tra employeeId bắt buộc/không trùng và truyền qua register/login/me/refresh.
Các regression test validation, throttling, logout và refresh rotation vẫn được chạy.
Các API test dùng H2. `AuthSchemaTests` khởi tạo từ `001_auth_schema.sql` và để
Hibernate validate schema thật; kiểm tra constraint và hash mật khẩu seed.
Kiểm tra seed PostgreSQL (array/DO/CTE), chạy lại không ghi đè dữ liệu và rollback
khi mapping xung đột cần dùng PostgreSQL tạm, không dùng database đang phục vụ ứng dụng.

Nếu môi trường chặn Mockito tự attach agent, chạy với agent có sẵn trong Maven cache:

```sh
./mvnw test -DargLine="-javaagent:/path/to/mockito-core-5.23.0.jar"
```
