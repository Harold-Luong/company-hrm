# Auth Service

**Mục lục**

- [Cấu hình và chạy độc lập](#cấu-hình-và-chạy-độc-lập)
- [Thứ tự chạy local](#thứ-tự-chạy-local)
- [Role và field](#role-và-field)
- [API hiện có](#api-hiện-có)
- [Cấp tài khoản qua Employee](#cấp-tài-khoản-qua-employee)
- [Khởi tạo database và seed](#khởi-tạo-database-và-seed)
- [Kiểm thử](#kiểm-thử)
- [Cấu hình asymmetric JWT (RS256)](#cấu-hình-asymmetric-jwt-rs256)
- [Email kích hoạt tài khoản qua Resend](#email-kích-hoạt-tài-khoản-qua-resend)
- [Login throttling](#login-throttling)

Module giữ JWT access token và refresh session hiện có. User chứa thông tin tài khoản;
User lưu `employee_id` bắt buộc/unique để liên kết một-một với Employee, kể cả tài khoản HR/ADMIN. Không đưa mã nhân viên,
phòng ban, trạng thái lao động hoặc ngày vào/nghỉ việc vào User.

Chạy trực tiếp Auth tại `8081`; Gateway là `8080`. Health/Swagger bên dưới dùng cổng trực tiếp; health qua Gateway yêu cầu access token. Xem [hướng dẫn chạy độc lập](../../../README.md#local) cho `.env` từng service. Mẫu `.env` trỏ PostgreSQL `55432` và khóa `back-end/modules/auth-service-main/keys`; nếu tự tạo database/khóa theo hướng dẫn bên dưới, sửa các biến `SPRING_DATASOURCE_*` và `JWT_*` khớp vị trí thực tế trước khi nạp.

## Cấu hình và chạy độc lập

Project Maven riêng, Java 21, cổng mặc định 8081. Build/chạy từ thư mục
auth-service-main; không cần chạy Maven ở gốc repository hoặc build service khác.

Điều kiện: PostgreSQL/schema và hai cặp RSA access/refresh. Để thử login bằng seed mà không cần Kafka/mail, đặt `HRM_EVENTS_ENABLED=false` và `AUTH_ACTIVATION_ENABLED=false`.

1. Chuẩn bị database/user riêng và SQL theo phần database trong `README` này.
2. Tạo `.env` từ [.env.example](.env.example) nếu chưa có, rồi sửa host/port,
   mật khẩu, đường dẫn khóa và các cờ tính năng theo môi trường thực tế.
3. Nạp biến vào đúng terminal hoặc Run Configuration của IDE trước khi chạy.

Các lệnh sau dùng Git Bash/Bash, bắt đầu từ gốc repository:

```bash
cd back-end/modules/auth-service-main
test -f .env || cp .env.example .env
# Sửa và lưu .env trước khi chạy các lệnh tiếp theo.
set -a
source ./.env
set +a
mvn spring-boot:run
```

Maven/Spring Boot không tự đọc .env. IDE cần working directory là project và
biến môi trường riêng; thay `.env` phải nạp lại và restart project đó. Mẫu dùng
khóa tại `back-end/modules/auth-service-main/keys`, tính từ working directory của
từng module; máy mới tạo khóa một lần theo hướng dẫn JWT của Auth.

| Biến môi trường | Cấu hình cho project này |
| --- | --- |
| `SERVER_PORT` | 8081 |
| `SPRING_DATASOURCE_URL` | jdbc:postgresql://localhost:5432/auth_db; sửa port nếu DB dùng 55432 hoặc port khác |
| `SPRING_DATASOURCE_USERNAME` | auth_user |
| `SPRING_DATASOURCE_PASSWORD` | Mật khẩu của user database này |
| `JWT_ACCESS_PUBLIC_KEY` | file:/duong-dan-tuyet-doi/access-public.pem |
| `JWT_ACCESS_ISSUER` / `JWT_ACCESS_AUDIENCE` | auth-service / hrm-api-access, khớp Auth |

Auth cần thêm `JWT_ACCESS_PRIVATE_KEY`, `JWT_REFRESH_PRIVATE_KEY` và
`JWT_REFRESH_PUBLIC_KEY`; dùng hai cặp RSA riêng theo phần JWT bên dưới.

`SPRING_DATASOURCE_*` ưu tiên hơn biến `<SERVICE>_DB_*`; `SERVER_PORT` ưu tiên hơn
biến cổng riêng nếu có. Không giữ hai bộ giá trị mâu thuẫn trong environment.
Dừng bằng `Ctrl+C` trong terminal này. Build riêng bằng `mvn package`; kiểm thử theo
phần kiểm thử bên dưới. Với backend có wrapper có thể dùng `./mvnw` hoặc `mvnw.cmd`
thay mvn. Không bắt buộc `run-all.sh` hoặc Docker Compose để chạy project.

## Thứ tự chạy local

Từ thư mục gốc repository, vào `back-end/modules/auth-service-main`; dùng JDK 21
và PostgreSQL. Làm theo thứ tự:

1. Chuẩn bị role `auth_user` và database `auth_db` nếu chưa có.
2. Chạy [001_auth_schema.sql](docs/sql/001_auth_schema.sql) để tạo schema đầy đủ.
3. Chạy [002_auth_seed.sql](docs/sql/002_auth_seed.sql) nếu cần tài khoản local để đăng nhập.
4. Tạo RSA keys một lần theo [hướng dẫn JWT](#jwt-tạo-khóa-cho-local).
5. Chuẩn bị/nạp `.env` rồi khởi động Auth theo [phần chạy Auth](#3-chạy-auth).

Lệnh SQL và danh sách tài khoản nằm ở [phần khởi tạo database và seed](#khởi-tạo-database-và-seed).
Không cần chạy thêm script migration Auth rời cho Kafka hoặc email: schema mới đã
bao gồm cả hai. Seed không gửi mail hoặc tạo phiên đăng nhập.

## Role và field

| Role | Ý nghĩa |
| --- | --- |
| EMPLOYEE | Tài khoản nhân viên; vẫn cần Employee liên kết và đang active để chấm công |
| MANAGER | Quản lý; phạm vi team phải được module nghiệp vụ kiểm tra |
| HR | Quản trị nghiệp vụ nhân sự; không được tạo tài khoản |
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

`POST /api/v1/auth/register` yêu cầu access token của tài khoản có ADMIN, được kiểm tra ở HTTP và service
(`@PreAuthorize`). Tham khảo [Spring method security](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html).
Request gồm `email`, `password`, `employeeId` bắt buộc và `roles` tùy chọn dưới dạng mảng. Bỏ qua/null `roles` sẽ dùng `["EMPLOYEE"]`.
`employeeId` là chuỗi UUID (ví dụ `550e8400-e29b-41d4-a716-000000001001`); thiếu/null/sai định dạng trả 400. Nhân viên đã có tài khoản trả 400.
Cột `users.employee_id` có kiểu PostgreSQL `uuid`. Database áp dụng NOT NULL và UNIQUE cho `users.employee_id`. Đây là ID tham chiếu tới Employee;
Auth không gọi Employee để kiểm tra nhân viên tồn tại hoặc đang active và chưa có foreign key liên dịch vụ.
Mảng rỗng, phần tử null, role không hợp lệ hoặc truyền chuỗi thay mảng trả 400; role trùng được gộp.
Chỉ chấp nhận bốn role: EMPLOYEE, MANAGER, HR, ADMIN. Không truyền token trả 401;
không có ADMIN hoặc account inactive trả 403; role không hợp lệ trả 400.

Đăng nhập tài khoản Admin trước khi tạo tài khoản từ UI/Swagger. Không có đăng ký công khai.
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
  `employeeId`, `correlationId`; `data.requestedBy` giữ người gửi yêu cầu gốc cho
  cả kết quả thành công/thất bại để Notification gửi SSE đúng người. Đây là trường
  bổ sung tùy chọn trong schema v1; kết quả cũ đã lưu không được sửa/phát lại.
  Thành công có `accountStatus` và `accountVersion`,
  không cần trả `accountId` cho Employee.
- Account ban đầu chờ kích hoạt, chưa được đăng nhập; Auth quản lý lời mời và
  thiết lập mật khẩu. Không gửi mật khẩu/hash/token kích hoạt trong message.
  Đã bổ sung `activation_pending`, phân biệt chờ kích hoạt với bị khóa. Account
  có hash từ bí mật ngẫu nhiên riêng không được giữ lại/phát ra ngoài, không có
  mật khẩu chung. Đã có [email kích hoạt qua Resend](#activation), token một lần
  có hạn dùng, API đặt mật khẩu và API HR/Admin gửi lại lời mời.

Kích hoạt đã phát `AccountStatusChanged` và Employee cập nhật theo version.
Còn cần triển khai: API khóa/mở và sự kiện tương ứng, DLT, đối soát và vận hành production.

Employee theo dõi `PENDING/SUCCEEDED/FAILED` riêng với bản sao `accountStatus`.
Auth không cập nhật trạng thái lao động `EmployeeStatus`.

Khi bật sự kiện, `/register` cũ trả `409` để không tạo Account thiếu Outbox.
Khi cờ tắt (mặc định), API cũ vẫn dùng được như mô tả ở trên. Login/refresh/filter
đều từ chối Account chờ kích hoạt. Auth cần schema đầy đủ trong `docs/sql/001_auth_schema.sql` trước khi start,
kể cả khi chưa bật cờ sự kiện hoặc email.
Xem [hướng dẫn chạy](../employee-service/README.md#provisioning) và
[hợp đồng/các phase](../../../README.md#events).

## Khởi tạo database và seed

Auth có đúng hai script trong `docs/sql`:

- [001_auth_schema.sql](docs/sql/001_auth_schema.sql): schema đầy đủ, bao gồm tài khoản, phiên đăng nhập,
  provisioning Kafka, activation token và hàng đợi email.
- [002_auth_seed.sql](docs/sql/002_auth_seed.sql): dữ liệu local có đủ bốn vai trò và account bị khóa.

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
curl --fail http://localhost:8081/api/v1/auth/health-check
```

Kỳ vọng `status=UP`, `database=UP`; đây không phải kiểm tra Kafka/Resend. Mở Swagger
ở `http://localhost:8081/swagger-ui.html` để thao tác API.

Cấu hình mail/worker xem [kích hoạt tài khoản](#activation). Đăng nhập:

```bash
curl -X POST http://localhost:8081/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@company.com","password":"Admin@123456"}'
```

Account `disabled@company.com` trả `403`. ADMIN không tự có quyền HR; cả hai vai
trò đều được phép gọi các API cấp tài khoản theo chính sách hiện tại.

## Kiểm thử

`./mvnw test` kiểm tra một/nhiều role qua register/login/refresh/me, mặc định EMPLOYEE, role trùng,
từ chối tập rỗng/null element/role ngoài enum, quyền ADMIN ở HTTP/service,
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

<a id="jwt"></a>

## Cấu hình asymmetric JWT (RS256)

Access token và refresh token dùng hai cặp RSA riêng biệt. Auth service ký bằng private key,
xác thực bằng public key và chỉ chấp nhận RS256. Các claim, thời hạn token và cơ chế refresh
session được giữ nguyên. Xem [JJWT 0.12.6](https://github.com/jwtk/jjwt/blob/0.12.6/README.adoc#signed-jwts).

<a id="jwt-tạo-khóa-cho-local"></a>

### Tạo khóa cho local

Trước khi chạy Auth, chuẩn bị database bằng [hướng dẫn schema và seed mới](#khởi-tạo-database-và-seed):
`001_auth_schema.sql`, sau đó `002_auth_seed.sql` nếu cần tài khoản local.
Tạo khóa không thay thế bước tạo schema.

Mẫu `.env` của Auth dùng `file:./keys/<tên-khóa>.pem`; các service khác dùng
`file:./keys/access-public.pem` với bản sao public key được cấp riêng trong thư mục
`keys/` của chính service. Chỉ phân phối `access-public.pem`; private key và bộ
khóa refresh do Auth giữ. Các service có thể được triển khai trên các máy khác nhau.
Nếu đã có đủ hai cặp khóa tại đây, dùng lại và bỏ qua lệnh tạo khóa bên dưới.
Nếu môi trường đang dùng bộ khóa ở vị trí khác, giữ nguyên bộ khóa đó và đặt
đường dẫn thực tế trong `.env`/IDE của các service; không tạo lại khóa khi restart.

Từ thư mục gốc repository, vào module rồi tạo khóa một lần:

```bash
cd back-end/modules/auth-service-main
mkdir -p keys
chmod 700 keys
(
  umask 077
  for purpose in access refresh; do
    if [ -e "keys/$purpose-private.pem" ] || [ -e "keys/$purpose-public.pem" ]; then
      echo "Khóa $purpose đã tồn tại; giữ nguyên để tiếp tục xác thực token cũ." >&2
      exit 1
    fi
  done
  for purpose in access refresh; do
    openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out "keys/$purpose-private.pem" || exit 1
    openssl pkey -in "keys/$purpose-private.pem" -pubout -out "keys/$purpose-public.pem" || exit 1
  done
)
```

Private key phải là PEM PKCS#8 (`BEGIN PRIVATE KEY`); public key là PEM X.509
(`BEGIN PUBLIC KEY`). Khóa phải có ít nhất 2048 bit. Ứng dụng nạp khóa một lần khi khởi động
và báo lỗi nếu thiếu/sai định dạng, hai khóa không khớp, hoặc access/refresh dùng chung cặp khóa.
Thư mục `keys/` đã được bỏ qua trong Git. Khóa trong `src/test/resources/jwt/` chỉ dành cho test,
không dùng để chạy ứng dụng thực tế.

Sau khi chuẩn bị khóa Auth, cấp bản sao `access-public.pem` cho thư mục `keys/` của
Employee, Calendar, Leave, Attendance, Notification và Gateway. Khi triển khai
tách máy/container, pipeline hoặc người vận hành phân phối cùng public key đến
từng service; service không đọc file từ thư mục hoặc máy của Auth khi chạy.
Nếu mount key tại vị trí khác, đặt `JWT_ACCESS_PUBLIC_KEY=file:/duong-dan/access-public.pem`.
Khi thay khóa, cập nhật các bản sao và restart service để nạp lại.

<a id="jwt-khởi-động-sau-khi-có-schema-và-keys"></a>

### Khởi động sau khi có schema và keys

Nạp `.env` và khởi động trong thư mục Auth theo [chế độ chạy Auth](#3-chạy-auth).

Nếu chỉ kiểm tra JWT bằng tài khoản seed, dùng `HRM_EVENTS_ENABLED=false` và
`AUTH_ACTIVATION_ENABLED=false`; không cần Kafka hoặc Resend cho chế độ này.
Account seed mới `admin@company.com` dùng `Admin@123456`. Account đã tồn tại giữ
nguyên mật khẩu vì seed không ghi đè thông tin đăng nhập.

<a id="jwt-cấu-hình-môi-trường"></a>

### Cấu hình môi trường

Mặc định ứng dụng đọc bốn file trong `./keys/` theo working directory. Khi triển khai,
mount khóa từ secret storage và đặt các biến sau với đường dẫn thực tế:

```bash
export JWT_ACCESS_PRIVATE_KEY=file:/run/secrets/jwt/access-private.pem
export JWT_ACCESS_PUBLIC_KEY=file:/run/secrets/jwt/access-public.pem
export JWT_REFRESH_PRIVATE_KEY=file:/run/secrets/jwt/refresh-private.pem
export JWT_REFRESH_PUBLIC_KEY=file:/run/secrets/jwt/refresh-public.pem
```

Chỉ auth service giữ private key. Service nghiệp vụ nhận **access public key**, xác minh chữ ký
RS256, `iss=auth-service`, `aud=hrm-api-access` và thời hạn `exp` trước khi tin các claim.
Refresh token chỉ được xử lý tại auth service, với `aud=hrm-api-refresh` và `type=refresh`.
Hiện phân phối public key qua file cấu hình; chưa có JWKS endpoint hay cơ chế nhiều khóa/`kid`.

<a id="jwt-chuyển-đổi-và-thay-khóa"></a>

### Chuyển đổi và thay khóa

`JWT_SECRET_ACCESS` và `JWT_SECRET_REFRESH` không còn được sử dụng. Token HMAC cũ sẽ bị từ chối
sau khi chuyển cấu hình; người dùng cần đăng nhập lại. Không cần đổi schema database.
Giữ khóa ổn định giữa các lần restart và đồng bộ chúng giữa các instance auth service.
Thay cặp khóa làm token tương ứng đã phát hành mất hiệu lực; cần restart để nạp khóa mới,
đồng thời cập nhật access public key tại các service xác thực token.

Chạy `./mvnw test` để kiểm tra RS256, public-key verification, từ chối chữ ký/thuật toán/claim sai,
cấu hình khóa không hợp lệ và các luồng API hiện có.

<a id="activation"></a>

## Email kích hoạt tài khoản qua Resend

Auth tự tạo lời mời khi xử lý thành công `EmployeeAccountRequested`, nếu
`AUTH_ACTIVATION_ENABLED=true`. Account, hash token, mail outbox và kết quả Kafka
commit trong cùng transaction. Gửi HTTP tới Resend diễn ra ở worker sau commit;
lỗi Resend không xóa Account hoặc làm thất bại yêu cầu cấp Account đã commit.

Các ví dụ health/Swagger gọi trực tiếp Auth `8081`; link kích hoạt `5173/activate` mở frontend Vue. Cấu hình `.env`/database/khóa theo [hướng dẫn chạy độc lập](../../../README.md#local); đường dẫn khóa trong mẫu `.env` có thể khác `keys/` của hướng dẫn tạo khóa thủ công.

<a id="activation-1-cấu-hình-và-chạy"></a>

### 1. Cấu hình và chạy

Chuẩn bị database/role theo [hướng dẫn Auth](#khởi-tạo-database-và-seed),
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

Chuẩn bị RSA keys theo [hướng dẫn JWT](#jwt-tạo-khóa-cho-local) nếu chưa có.

Điền `.env` (đã bị gitignore) hoặc environment variables trong IDE:

| Biến | Giá trị / mục đích |
|---|---|
| `RESEND_API_KEY` | Thay `xxxx` bằng API key Resend của bạn; chỉ dùng ở backend |
| `RESEND_FROM` | Ví dụ `Company HRM <hrm@tenmiencuaban.com>`; dùng domain đã verify trong Resend |
| `AUTH_ACTIVATION_TOKEN_SECRET` | Chuỗi base64 từ `openssl rand -base64 32`; giữ ổn định, không dùng API key/JWT key làm secret này |
| `AUTH_ACTIVATION_ENABLED` | Đặt `true` sau khi có schema và cấu hình; mặc định `false` |
| `AUTH_ACTIVATION_TOKEN_TTL` | Mặc định `24h`, lớn hơn 0 và tối đa `7d` |
| `AUTH_ACTIVATION_FRONTEND_URL` | Local `http://localhost:5173/activate`; production `https://hrm.example.com/activate` |
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

<a id="activation-kiểm-tra-cấu-hình-và-cách-chạy-local"></a>

### Kiểm tra cấu hình và cách chạy local

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
   theo [hướng dẫn Kafka](../../../README.md#kafka). Chạy `docs/sql/001_auth_schema.sql`
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
   curl --fail http://localhost:8081/api/v1/auth/health-check
   ```

   Kỳ vọng `status=UP`, `database=UP`. Endpoint này không kiểm tra Resend hoặc
   migration. Auth phục vụ Swagger tại `http://localhost:8081/swagger-ui.html`.
8. Khởi động Employee với `HRM_EVENTS_ENABLED=true`, gửi yêu cầu cấp account qua
   Employee. Nếu Account đã được tạo khi gửi mail còn tắt, Auth không tự tạo lại
   lời mời khi restart: đăng nhập HR/Admin và gọi
   `POST /api/v1/auth/activation-invitations/{employeeId}` trong Swagger.
9. Dùng `GET /api/v1/auth/activation-invitations/{employeeId}` để theo dõi. Không
   có lời mời trả `404`; `PENDING` chờ worker (poll mặc định 5 giây); `SENT` đã được
   Resend chấp nhận. `FAILED` với `RESEND_HTTP_401/403` cần kiểm tra key, sender,
   domain và giới hạn người nhận trong dashboard Resend. Sau khi sửa cấu hình,
   restart Auth rồi tạo lời mời mới; email đã `FAILED` không tự retry.

`http://localhost:5173/activate` trong email chỉ dùng được trên chính máy chạy
frontend. Khi người nhận mở bằng điện thoại/máy khác, cấu hình URL HTTPS truy cập được.
Không khởi động Auth để “chỉ kiểm tra cấu hình” nếu đã bật activation và có mail
pending mà bạn chưa muốn gửi: worker có thể gửi ngay khi ứng dụng chạy.

<a id="activation-2-link-và-trang-đặt-mật-khẩu"></a>

### 2. Link và trang đặt mật khẩu

```text
https://hrm.example.com/activate?token=<opaque-token>
```

Trang `/activate` do frontend Vue phục vụ, không cần đăng nhập. Cấu hình
`AUTH_ACTIVATION_FRONTEND_URL` trỏ tới URL frontend, local mặc định là
`http://localhost:5173/activate`. Reverse proxy phục vụ Vue tại `/activate` và chuyển
`/api/v1/auth/*` qua Gateway đến Auth. Backend chỉ xử lý API kích hoạt;
`GET /activate` và `/activation/index.html` trên Auth/Gateway chuyển hướng đến URL
frontend đã cấu hình để các link email cũ vẫn dùng được. Không cấu hình URL này
trỏ ngược về đường dẫn chuyển hướng của Auth/Gateway vì sẽ tạo vòng lặp.
Khi cập nhật từ cấu hình cũ, sửa cả `.env` và khởi động lại Auth; thư đã gửi hoặc
đang chờ gửi với URL cũ vẫn đi qua chuyển hướng này.

Trang không dùng dịch vụ bên ngoài, có `Referrer-Policy: no-referrer`, không lưu
token ở local/sessionStorage và xóa query khỏi address bar sau khi đọc vào bộ nhớ.
Reload trang đã xóa query cần mở lại liên kết từ email. Cấu hình proxy/access log
không ghi query của `/activate` hoặc body của API kích hoạt. **GET không tiêu thụ
token**; trình quét email có thể mở link mà không kích hoạt account.

Mật khẩu: ít nhất 12 ký tự, tối đa 72 byte UTF-8 để tránh cắt ngầm với BCrypt.
Form yêu cầu nhập lại mật khẩu. Sau thành công, trang xóa mật khẩu khỏi form và
hiện nút **Đi đến đăng nhập** tới `/login`, dùng email và mật khẩu vừa đặt.
Tính năng này chỉ đặt mật khẩu lần đầu; chưa bổ sung quên mật khẩu hoặc đổi mật khẩu
cho account đã hoạt động.

<a id="activation-3-api"></a>

### 3. API

<a id="activation-kích-hoạt--public"></a>

#### Kích hoạt — public

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

<a id="activation-gửi-lại-lời-mời--hr-hoặc-admin"></a>

#### Gửi lại lời mời — HR hoặc ADMIN

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

<a id="activation-kiểm-tra-email-gần-nhất--hr-hoặc-admin"></a>

#### Kiểm tra email gần nhất — HR hoặc ADMIN

`GET /api/v1/auth/activation-invitations/{employeeId}` với Bearer access token.

Trả `invitationId`, `deliveryStatus`, `expiresAt`, `sentAt`, `attempts`, `errorCode`.
`PENDING/SENT/FAILED/CANCELLED` là trạng thái gửi mail; `SENT` nghĩa là Resend đã
chấp nhận email, **không chứng minh inbox đã nhận hoặc account đã kích hoạt**.
Không có lời mời trả `404`. Không trả token, API key hay nội dung mail.

<a id="activation-4-lưu-trữ-retry-và-đồng-bộ"></a>

### 4. Lưu trữ, retry và đồng bộ

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

<a id="activation-5-kiểm-tra"></a>

### 5. Kiểm tra

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

<a id="throttling"></a>

## Login throttling

`POST /api/v1/auth/login` reserves an account budget and an IP budget before
looking up the user or checking the password. Both reservations are atomic within
one application instance. Email keys are trimmed and lowercased with `Locale.ROOT`.
Unknown accounts are limited in the same way as existing accounts.

Defaults in `application.yaml`:

```yaml
auth:
  login-throttle:
    account-max-attempts: 10
    ip-max-attempts: 50
    window: 15m
    max-keys: 10000
```

These are fixed windows starting with the first admitted attempt for each key.
Every admitted login attempt counts, including successful logins. Success does
not reset either budget. Rejected requests do not consume budgets or extend their
windows. Validation failures (`400`) occur before the login service and do not
consume budgets; this limiter does not replace HTTP-level flood protection.

Exceeding either budget returns HTTP `429`, a `Retry-After` header in whole seconds,
and the usual error body:

```json
{"code":"429","message":"Too many login attempts. Please try again later."}
```

The budgets are held in memory and reset on restart. They are **not shared across
replicas**. For multiple instances, move both counters to a shared store with atomic
updates (for example Redis), or enforce corresponding limits at a shared gateway.
The store contains at most `max-keys` account/IP entries. Expired entries are pruned
on incoming attempts; at capacity, requests needing new keys return `429` until
space expires. Active entries are not evicted to admit attacker-controlled keys.

<a id="throttling-local-setup-for-testing"></a>

### Local setup for testing

Follow the [Auth setup guide](#khởi-tạo-database-và-seed): run
`docs/sql/001_auth_schema.sql`, then `docs/sql/002_auth_seed.sql` in the Auth database.
New seed accounts use `Admin@123456`; `employee@company.com` is active and
`disabled@company.com` is inactive. Replaying the seed preserves existing passwords
and account state, so an older account may still have its previous password.

Prepare RSA keys and load `.env` using the [Auth run instructions](#3-chạy-auth).
Login throttling tests do not require Kafka or email; both feature flags can be
`false`. There is no additional SQL migration for login throttling because its
counters are in memory. Use disposable local accounts for repeated login attempts.

<a id="throttling-client-ip-and-proxies"></a>

### Client IP and proxies

The controller uses `HttpServletRequest.getRemoteAddr()`. The default configuration
sets `server.forward-headers-strategy: none`; the application does not parse
`X-Forwarded-For` or `Forwarded` supplied by callers. Behind a reverse proxy this
means the proxy IP is used, so users behind that proxy share the IP budget.

Before deploying behind a proxy, either apply IP throttling at that proxy/gateway,
or explicitly configure trusted proxy processing in the servlet container so
`getRemoteAddr()` reflects the client. Restrict direct access to the application
and make the trusted proxy overwrite incoming forwarding headers. Do not simply
trust arbitrary forwarding headers from public requests.

<a id="throttling-failed-login-events"></a>

### Failed-login events

Logger `auth.login.audit` emits `WARN` events with the logging framework's timestamp:

```text
event=login_failed accountHash=<SHA-256 of normalized email> ip=<remote IP> reason=INVALID_PASSWORD
event=login_throttled accountHash=<hash> ip=<remote IP> retryAfterSeconds=900
```

Failure reasons are `UNKNOWN_ACCOUNT`, `INVALID_PASSWORD`, and `INACTIVE_ACCOUNT`.
These events survive rollback of the login database transaction. Passwords, password
hashes, tokens, raw emails and request bodies are not included in these audit events.
The account hash is a correlation identifier, not guaranteed anonymization.
Server/database failures are not mislabeled as bad credentials. Invalid request
bodies are handled by validation and are not login-failure audit events.

Events go to the application's configured log appenders, not a database audit table.
Configure log collection, restricted access, retention and alerts in the deployment;
high request volume can also create high log volume.

Design references: [OWASP authentication](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html#login-throttling)
and [OWASP logging](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html#data-to-exclude).
