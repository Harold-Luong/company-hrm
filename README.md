# Company HRM — Project Instructions

## 1. Bối cảnh dự án

Đây là dự án xây dựng một hệ thống **Human Resource Management (HRM)** cho doanh nghiệp.

Mục tiêu ban đầu là xây dựng hệ thống quản lý **Attendance / Check-in / Check-out**, sau đó mở rộng thành một nền tảng quản lý nhân sự và employee experience.

**Ưu tiên hiện tại: Release v0.1 — Attendance MVP**, cho phép nhân viên chấm công hằng ngày và HR kiểm tra, điều chỉnh công khi có sai sót. Các nghiệp vụ dưới đây là định hướng toàn sản phẩm, không phải phạm vi phải xây ngay. Phạm vi và tiêu chí hoàn thành từng release được quy định tại mục 22.

Phạm vi mặc định của MVP: một công ty, timezone `Asia/Ho_Chi_Minh`, một ca trong ngày cho mỗi nhân viên, web responsive và xác thực chấm công qua mạng công ty. Timezone là cấu hình nghiệp vụ; chưa triển khai multi-tenant, ca qua đêm hoặc nhiều ca/ngày trong v0.1.

Các nghiệp vụ dự kiến:

- Employee Management
- Employee Profile
- Organization / Department / Team
- Position / Job Title
- Attendance
- Check-in / Check-out
- Work Shift
- Overtime
- Leave Management
- Leave Request / Approval
- Company Holiday / Company Calendar
- Attendance Report
- Leave Balance
- Device Management
- Notification
- Sau này có thể mở rộng Payroll, Recruitment, Performance Management và các nghiệp vụ HR khác.

---

## 2. Định hướng kiến trúc

### Giai đoạn đầu

Sử dụng **Modular Monolith**, không triển khai microservices ngay từ đầu.

Toàn bộ hệ thống có thể nằm trong một repository:

```text
company-hrm/
```

Các module được tách biệt theo domain:

```text
employee/
organization/
attendance/
leave/
holiday/
shift/
overtime/
device/
notification/
```

Mỗi module phải có boundary tương đối rõ để sau này có thể tách thành microservice nếu hệ thống phát triển đủ lớn.

Danh sách trên là các domain dự kiến. Chỉ tạo module khi release hiện tại cần; v0.1 cần identity/access, employee, organization tối thiểu, shift và attendance. Adapter kiểm tra mạng nằm ở infrastructure của Attendance, chưa cần module Device độc lập.

Quy tắc boundary:

- Mỗi module sở hữu bảng, repository và logic ghi dữ liệu của mình.
- Module khác gọi application API/interface được công khai; không truy cập trực tiếp repository hoặc JPA entity nội bộ.
- Tham chiếu liên module bằng ID; không tạo entity graph xuyên module hoặc dependency vòng.
- Dùng lời gọi đồng bộ trong monolith khi cần nhất quán ngay. Chỉ thêm event/asynchronous processing khi có nhu cầu cụ thể.
- Company Calendar là read model tổng hợp từ các module sở hữu dữ liệu, không sở hữu lại Leave, Shift hoặc Holiday.

### Nguyên tắc

Không thiết kế hệ thống theo hướng "microservices-first".

Ưu tiên:

```text
Clean Domain Boundaries
        ↓
Modular Monolith
        ↓
Event-driven khi cần
        ↓
Microservices khi có lý do thực tế
```

Không đề xuất tách microservice chỉ vì một module tồn tại độc lập.

---

## 3. Attendance

Attendance là module đầu tiên và là nghiệp vụ quan trọng của hệ thống.

Thiết kế phải cho phép bổ sung nhiều phương thức Check-in / Check-out. v0.1 chỉ triển khai corporate network và thao tác điều chỉnh thủ công có phân quyền; chưa triển khai tất cả provider.

Ví dụ:

```text
CORPORATE_NETWORK
RFID
FINGERPRINT
FACE
QR_CODE
MANUAL
```

Không được thiết kế Attendance phụ thuộc trực tiếp vào Wi-Fi.

Wi-Fi, RFID, fingerprint, face recognition... được xem là **attendance source / attendance provider / integration**.

Ví dụ:

```text
Attendance
    │
    ├── Corporate Network Provider
    ├── RFID Provider
    ├── Fingerprint Provider
    ├── Face Provider
    └── Manual Provider
```

Domain Attendance phải độc lập với implementation cụ thể của từng thiết bị.

---

## 4. Wi-Fi Attendance

Trong v0.1, Check-in / Check-out được xác thực bằng corporate network. UI có thể gọi là chấm công qua Wi-Fi công ty, nhưng dữ liệu lưu `method=CORPORATE_NETWORK`: public IP chỉ xác nhận đường mạng truy cập, không chứng minh nhân viên đang kết nối Wi-Fi hoặc có mặt tại văn phòng.

Không chỉ dựa vào SSID vì SSID có thể bị giả mạo.

Quy tắc MVP:

- Backend kiểm tra source IP đáng tin cậy với allowlist IP/CIDR của địa điểm làm việc, áp dụng cho cả check-in và check-out trực tiếp.
- Chỉ nhận traffic qua reverse proxy/load balancer được cấu hình tin cậy; ngăn truy cập trực tiếp vào application từ Internet.
- Cấu hình cách proxy xử lý forwarded headers và duyệt chuỗi proxy từ phía tin cậy; không mặc định lấy phần tử đầu tiên của `X-Forwarded-For`.
- Không nhận IP, SSID hoặc trạng thái “đang ở công ty” do client gửi làm bằng chứng xác thực.
- VPN từ xa dùng chung public IP có thể vượt qua kiểm tra mạng. Trước pilot phải tách egress VPN khỏi allowlist hoặc ghi nhận rõ mức xác thực này được doanh nghiệp chấp nhận.
- Nếu không xác minh được nguồn mạng, từ chối thao tác trực tiếp và cho phép HR xử lý bằng correction có audit.

Sau MVP có thể kết hợp, khi hạ tầng/client thực sự cung cấp bằng chứng tin cậy:

- Corporate public IP
- Internal network / subnet
- Network/VLAN
- SSID/BSSID nếu client platform cho phép
- Device information
- Location information nếu nghiệp vụ yêu cầu

Không mặc định web browser hoặc cloud backend đọc được SSID/BSSID, private subnet hoặc VLAN của thiết bị người dùng. Các thông tin này cần khả năng platform hoặc tích hợp hạ tầng tương ứng.

Tham khảo cấu hình [forwarded headers của AWS ALB](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/x-forwarded-headers.html).

---

## 5. Attendance Event

Ưu tiên thiết kế Attendance theo hướng event/audit-friendly.

Ví dụ:

```text
CHECK_IN
CHECK_OUT
BREAK_START
BREAK_END
```

v0.1 chỉ triển khai `CHECK_IN` và `CHECK_OUT`. `BREAK_START` / `BREAK_END` thuộc release mở rộng. Phương thức ghi nhận nằm ở `method`; thao tác thủ công dùng `method=MANUAL`, không tạo thêm `MANUAL_CHECK_IN` / `MANUAL_CHECK_OUT`.

Nên lưu lại thông tin phục vụ audit:

```text
id
employee_id
work_schedule_assignment_id
attendance_session_id
event_type
event_at
received_at
method
source_ip
device_id
location_id
actor_user_id
idempotency_key
provider_event_id
created_at
```

Không nên chỉ lưu `check_in_at` và `check_out_at` nếu điều đó khiến hệ thống khó audit hoặc khó mở rộng.

Có thể có:

```text
attendance_events
attendance_sessions
attendance_corrections
attendance_daily
```

Trong đó:

- `attendance_events`: các lần ghi nhận gốc, chỉ thêm mới; không sửa hoặc xóa qua nghiệp vụ thông thường.
- `attendance_sessions`: trạng thái phiên làm việc theo ca được phân công, gồm thời điểm bắt đầu/kết thúc có hiệu lực.
- `attendance_corrections`: thao tác bổ sung, thay thế hoặc vô hiệu hóa một lần ghi nhận; lưu target, giá trị trước/sau, người thực hiện, lý do và thời điểm. Bổ sung lần bị thiếu phải xác định nhân viên, ca và loại event.
- `attendance_daily`: tổng hợp theo `(employee_id, work_date)` từ event, correction và snapshot lịch áp dụng; phải có khả năng tính lại theo cùng quy tắc.

Quy tắc dữ liệu:

- `event_at` là thời điểm nghiệp vụ; chấm công trực tiếp dùng thời gian server. `received_at` là thời điểm server nhận dữ liệu. Lưu các thời điểm dưới dạng instant bằng PostgreSQL `timestamptz`; timezone nghiệp vụ lưu riêng ở lịch áp dụng.
- `work_date` là ngày công theo lịch được phân công, không suy ra bằng ngày UTC hoặc ngày nhận event. Với ca qua đêm ở release sau, mặc định thuộc ngày bắt đầu ca tại timezone của ca.
- Lưu snapshot hoặc phiên bản bất biến của lịch/rule dùng tính công. Sửa lịch tương lai không tự thay đổi công quá khứ; việc tính lại do đổi rule phải là thao tác tường minh có audit.
- Trường không áp dụng có thể null: ví dụ corporate network không có `device_id` hoặc `provider_event_id`; correction phải có `actor_user_id` và lý do. Nguồn thiết bị sau này phải lưu danh tính integration thay cho user nếu không có tài khoản người dùng thực hiện.
- Event, correction và dữ liệu tổng hợp liên quan được cập nhật trong cùng transaction ở v0.1. Audit-friendly không đồng nghĩa phải triển khai event sourcing framework.

---

## 6. Employee và User

Không mặc định:

```text
User == Employee
```

Nên phân biệt:

```text
User
    └── Account / Authentication

Employee
    └── HR Profile
```

Một Employee có thể có hoặc chưa có User account.

v0.1: mỗi Employee liên kết tối đa một User; mỗi User bắt buộc liên kết một Employee qua `users.employee_id` duy nhất, kể cả tài khoản quản trị. Auth nhận/trả `employeeId` qua API và đưa `employee_id` vào access JWT; `sub` vẫn là ID tài khoản. Check-in trực tiếp yêu cầu User liên kết với Employee đang active. HR có thể ghi nhận thủ công cho Employee không có tài khoản, với phân quyền và audit tương ứng.

Employee MVP chỉ cần mã nhân viên duy nhất, họ tên, trạng thái làm việc, ngày vào/nghỉ, phòng ban, quản lý trực tiếp và liên kết tài khoản. Contract, hồ sơ mở rộng và cơ cấu tổ chức nhiều cấp thuộc release sau.

Employee có thể có:

- Profile
- Department
- Position
- Contract
- Employment status
- Attendance
- Leave
- Work schedule

---

## 7. Leave Management

Từ release Leave, hệ thống phải hỗ trợ:

- Company holiday
- Employee leave request
- Leave approval
- Leave rejection
- Leave balance
- Leave type

Ví dụ:

```text
ANNUAL_LEAVE
SICK_LEAVE
UNPAID_LEAVE
MATERNITY_LEAVE
```

Cần phân biệt:

```text
Company Holiday
```

với:

```text
Employee Leave
```

Company Holiday là ngày nghỉ chung của công ty.

Employee Leave là ngày nghỉ do một nhân viên đăng ký.

`COMPANY_HOLIDAY` không phải leave type và không tạo đơn nghỉ phép cho từng nhân viên. Holiday thuộc lịch công ty/địa điểm; Leave tham chiếu lịch đó khi tính thời lượng nghỉ.

Khi triển khai Leave phải xác định:

- Trạng thái `PENDING`, `APPROVED`, `REJECTED`, `CANCELLED` và quyền chuyển trạng thái.
- Người duyệt theo quan hệ quản lý, cơ chế bàn giao khi đổi quản lý và quy tắc không tự duyệt.
- Tính phép theo phần thời gian làm việc đã lên lịch; loại trừ ngày lễ, ngày nghỉ và giờ nghỉ giữa ca, xử lý nghỉ nửa ngày/theo giờ và đơn chồng lấn.
- Sổ biến động phép để audit việc cấp, trừ, hoàn và điều chỉnh phép; số dư là dữ liệu tổng hợp. Duyệt/hủy và cập nhật phép phải atomic, idempotent, không trừ hoặc hoàn hai lần.
- Nghỉ được duyệt cập nhật đánh giá công nhưng không xóa event chấm công thực tế.

---

## 8. Company Calendar

Web application cần có khả năng hiển thị:

- Company holidays
- Working days
- Employee leave
- Shift
- Overtime
- Các event liên quan đến HR

Ví dụ:

```text
Company Calendar
├── Holiday
├── Working Day
├── Leave
├── Shift
└── Overtime
```

Calendar đầy đủ thuộc release Leave/Calendar. MVP chỉ cần hiển thị lịch làm việc cá nhân. Calendar dùng chung chỉ hiển thị thông tin nghỉ cần thiết theo quyền; lý do nghỉ và tài liệu đính kèm không công khai cho toàn công ty.

---

## 9. Work Shift

Shift là domain riêng.

Ví dụ:

```text
Morning Shift
08:00 → 17:30
Break: 12:00 → 13:00
Grace Period: 15 minutes
```

Không hard-code giờ làm việc trong Attendance Service.

Attendance phải lấy rule từ Shift / Work Schedule.

Ví dụ:

```text
Employee
    ↓
Work Schedule
    ↓
Shift
    ↓
Attendance
```

Điều này cho phép hỗ trợ nhiều ca làm việc trong tương lai.

### Quy tắc lịch và tính công cho v0.1

- Phân biệt mẫu ca (`work_shifts`), lịch làm việc (`work_schedules`) và ca đã phân công cho một nhân viên/ngày (`work_schedule_assignments`).
- Mỗi nhân viên có tối đa một ca được phân công trong một ngày công. MVP chỉ chấp nhận ca bắt đầu và kết thúc trong cùng ngày địa phương; từ chối cấu hình ca qua đêm hoặc nhiều ca/ngày bằng validation rõ ràng.
- Lưu thời điểm bắt đầu/kết thúc, timezone, giờ nghỉ cố định và grace period trong snapshot ca được phân công. HR có thể đánh dấu ngày không làm việc/ngày ngoại lệ trước khi có Holiday module.
- Mỗi ca có cửa sổ check-in/check-out cấu hình rõ, nằm trong ngày công ở MVP và bao phủ thời gian ca. Ngoài cửa sổ hoặc không có ca thì từ chối thao tác trực tiếp; HR xử lý bằng correction. Không đặt giới hạn cửa sổ ngầm trong code.
- Phiên chuyển `NOT_STARTED → OPEN → CLOSED`. Không check-out khi chưa có check-in; không tạo phiên thứ hai cho cùng ca trong MVP. Retry cùng request không tạo thêm event.
- Qua hạn check-out mà phiên còn mở thì đánh dấu `MISSING_CHECK_OUT`; không tự tạo check-out ở giờ kết thúc ca và không cộng thời gian vô hạn. Lần chấm công ngày sau không được tự ghép vào phiên cũ.
- Sau ngưỡng bắt đầu ca cộng grace period mới gắn cờ đi muộn; check-out trước giờ kết thúc ca gắn cờ về sớm. Lưu thời gian thực tế, không làm tròn hoặc điều chỉnh event để khớp lịch.
- Thời lượng có mặt hợp lệ là khoảng check-in/check-out sau khi trừ phần giao với giờ nghỉ không tính công; khoảng không hợp lệ phải báo lỗi. Lưu riêng phần giao với giờ làm việc theo lịch; thời gian ngoài lịch chưa được tự công nhận là overtime hoặc dùng tính lương.
- Chưa đủ cặp vào/ra thì thời lượng ở trạng thái chưa xác định, không hiển thị như 0 giờ đã chốt. Không có event sau khi hết ca được đánh dấu `NO_RECORD`, chưa tự kết luận nghỉ không phép khi chưa có Leave.

Ca qua đêm, nhiều ca/ngày, nhiều phiên vào/ra và nghỉ giữa ca theo event thuộc v0.2. Rule chấm công phải được mở rộng trước khi bật các kiểu lịch đó.

---

## 10. Device Integration

Khi tích hợp thiết bị chấm công, không đưa logic vendor trực tiếp vào Attendance domain.

Thiết kế theo adapter/integration:

```text
integration/
├── wifi/
├── rfid/
├── fingerprint/
├── face/
└── vendors/
```

Ví dụ:

```text
Fingerprint Device
        ↓
Vendor Adapter
        ↓
Device Event
        ↓
Attendance Service
        ↓
Attendance Event
```

Nếu sau này đổi nhà cung cấp thiết bị, chỉ cần thay đổi adapter/integration tương ứng.

Corporate network được triển khai từ v0.1. RFID/fingerprint/face/vendor integration thuộc release riêng. Khi tích hợp thiết bị, cần xác thực nguồn gửi, ánh xạ employee/device, chống trùng theo `(provider, device_id, provider_event_id)`, xử lý event đến muộn/sai thứ tự và đồng hồ thiết bị lệch. Event chưa ánh xạ hoặc chưa hợp lệ phải được giữ để đối soát, không âm thầm bỏ hoặc tính công ngay.

---

## 11. Database

Ưu tiên PostgreSQL.

Thiết kế database theo domain, tránh tạo một bảng khổng lồ chứa toàn bộ HRM.

Ví dụ:

```text
employees
users
departments
positions

attendance_events
attendance_sessions
attendance_corrections
attendance_daily

work_shifts
work_schedules
work_schedule_assignments

leave_types
leave_requests
leave_balances
leave_balance_entries

company_holidays

devices
device_events
```

Foreign key, unique constraint, check constraint và index phải được sử dụng phù hợp với nghiệp vụ.

Không tạo index một cách máy móc.

Danh sách này mô tả schema theo roadmap; chỉ tạo bảng phục vụ release đang triển khai. v0.1 chưa cần `positions`, các bảng Leave/Holiday hoặc Device. Cấu hình địa điểm và allowlist mạng thuộc Attendance; phân quyền thuộc identity/access.

Ràng buộc tối thiểu MVP: mã nhân viên duy nhất, liên kết User–Employee một-một khi có liên kết, một assignment cho mỗi nhân viên/ngày, một session cho mỗi assignment, một daily summary cho mỗi nhân viên/ngày, thời gian kết thúc không trước thời gian bắt đầu và khóa idempotency duy nhất trong phạm vi actor/operation. Ràng buộc một ca/ngày được thay đổi bằng migration khi triển khai v0.2.

---

## 12. Backend

Backend ưu tiên:

- Java
- Spring Boot
- Spring Security
- Spring Data JPA / Hibernate
- PostgreSQL
- Flyway hoặc Liquibase
- REST API

Nếu cần asynchronous processing:

- Spring events
- Message Queue
- AWS SQS
- EventBridge

Không thêm infrastructure phức tạp nếu nghiệp vụ chưa cần.

v0.1 xử lý chấm công đồng bộ trong một ứng dụng Spring Boot và PostgreSQL; chưa cần queue hoặc Redis. Spring application events không được mặc định là bất đồng bộ hay có khả năng giao nhận bền vững. Khi release sau cần tác vụ sau commit không được mất, thiết kế cơ chế lưu bền vững, retry và chống xử lý trùng trước khi chọn broker. Tham khảo [Spring ApplicationContext events](https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html).

---

## 13. Authentication / Authorization

Hệ thống phải phân biệt:

```text
Authentication
```

và:

```text
Authorization
```

Có thể có các role:

```text
EMPLOYEE
MANAGER
HR
ADMIN
```

Nhưng authorization phải dựa trên nghiệp vụ thực tế.

Ví dụ:

Employee:

```text
View own attendance
Create own leave request
View company calendar
```

Manager:

```text
View team attendance
Approve team leave
```

HR:

```text
Manage employees
Manage attendance
Manage leave
Manage shifts
```

Admin:

```text
System configuration
Device management
Access control
```

Phạm vi dữ liệu bắt buộc:

- Employee chỉ chấm công và xem dữ liệu của chính mình; backend suy ra Employee từ principal, không tin `employee_id` do client gửi để tự chấm công.
- v0.1 Manager được xem nhân viên báo cáo trực tiếp đang thuộc phạm vi quản lý; quyền mặc định không mở rộng sang mọi phòng ban. Khi chuyển quản lý, quản lý cũ mất quyền truy cập, quản lý mới xem lịch sử của nhân viên trong phạm vi hiện tại; HR giữ quyền đối soát toàn công ty.
- HR được quản lý công trong phạm vi công ty, nhưng không tự điều chỉnh công của mình; cần HR khác hoặc người được cấp quyền điều chỉnh độc lập. Mọi correction lưu người thực hiện, lý do và giá trị trước/sau.
- Admin quản trị tài khoản/cấu hình; role Admin không tự cấp quyền đọc toàn bộ dữ liệu HR hoặc sửa công nếu chưa có quyền nghiệp vụ tương ứng.
- Kiểm tra quyền ở backend cho cả API danh sách, chi tiết, export và thao tác ghi. Giao diện ẩn nút không thay thế authorization.
- MVP chưa có duyệt đơn; quy tắc Manager/HR duyệt Leave chỉ được bật cùng release Leave. Không ai được tự duyệt đơn của mình.

---

## 14. API Design

API nên thiết kế theo domain.

Ví dụ:

```text
/api/employees
/api/attendance
/api/leave
/api/holidays
/api/shifts
/api/devices
```

Không tạo API theo database table một cách máy móc.

Ví dụ ưu tiên:

```text
POST /api/attendance/check-in
POST /api/attendance/check-out
```

thay vì cho client trực tiếp:

```text
POST /api/attendance-events
```

đối với các business operation quan trọng.

API tối thiểu v0.1:

```text
POST /api/attendance/check-in
POST /api/attendance/check-out
GET  /api/attendance/me
GET  /api/work-schedules/me
GET  /api/attendance/reports
POST /api/attendance/corrections
```

Các endpoint ghi hỗ trợ `Idempotency-Key`; backend kiểm tra phạm vi actor/operation và nội dung request. Cùng key/cùng nội dung trả lại kết quả đã lưu; cùng key/khác nội dung trả conflict. API correction tách quyền với API tự chấm công. Lịch sử/báo cáo hỗ trợ khoảng ngày, phân trang và lọc theo phạm vi được phép.

---

## 15. Frontend

Frontend là web application phục vụ hai nhóm chính:

### Employee

```text
Dashboard
My Profile
Attendance
Leave
Company Calendar
Work Schedule
Notifications
```

### HR / Manager / Admin

```text
Dashboard
Employees
Departments
Attendance
Leave Requests
Shifts
Devices
Reports
Company Calendar
Settings
```

UI cần responsive và có thể sử dụng tốt trên desktop và mobile.

Danh sách màn hình trên là định hướng dài hạn. v0.1 chỉ triển khai đăng nhập, chấm công/trạng thái hôm nay, lịch sử và lịch làm việc cá nhân; màn hình HR quản lý nhân viên tối thiểu, phân ca, bảng công/correction/export; màn hình Admin quản lý tài khoản và cấu hình mạng. Các chức năng chưa phát hành không xuất hiện như tính năng khả dụng.

---

## 16. Business Rules

Khi phân tích nghiệp vụ, luôn xác định rõ:

1. Actor
2. Preconditions
3. Business rules
4. Main flow
5. Alternative flow
6. Error cases
7. Database impact
8. Authorization
9. Audit requirements

Ví dụ với Check-in:

```text
Actor:
Employee

Preconditions:
- Employee active
- User authenticated
- Employee được suy ra từ tài khoản, đang trong thời gian làm việc tại công ty
- Source IP đã xác minh thuộc allowlist corporate network
- Có assignment hợp lệ và đang trong cửa sổ check-in
- Phiên của assignment đang NOT_STARTED

Business rules:
- Xử lý idempotency trước khi áp dụng lại thao tác nghiệp vụ
- Kiểm tra trạng thái và cập nhật bằng concurrency control ở database
- Dùng thời gian server; xác định work_date từ assignment
- Ghi nhận thời gian thực tế, xác định late từ snapshot ca
- Lưu attendance event, mở session, cập nhật daily và kết quả idempotency atomically

Result:
Một CHECK_IN event được tạo, session OPEN, daily được cập nhật
Retry cùng request trả lại kết quả trước, không thêm event
```

Check-out đóng đúng session của ca, không chỉ tìm một check-in bất kỳ trong ngày. Correction yêu cầu quyền độc lập, lý do và phiên bản dữ liệu đang sửa; sau khi áp dụng phải kiểm tra lại thứ tự vào/ra và tính lại session/daily trong cùng transaction. Các tình huống từ chối phải trả mã lỗi nghiệp vụ và hướng xử lý rõ ràng cho UI.

---

## 17. Code Quality

Ưu tiên:

- Clean Code
- SOLID
- Separation of Concerns
- Domain-driven module boundaries
- Dependency Injection
- Transaction boundary rõ ràng
- Validation ở đúng layer
- Exception handling nhất quán
- Auditability

Không over-engineer.

Nếu một abstraction chưa có giá trị thực tế, không tạo abstraction chỉ để "đẹp kiến trúc".

---

## 18. Transaction

Transaction phải được đặt dựa trên business operation.

Ví dụ:

```text
Check-in
    ├── Validate
    ├── Create Attendance Event
    └── Update Daily Attendance
```

Nếu các operation phải atomic, chúng phải nằm trong cùng transaction.

Đặc biệt chú ý:

- Transaction propagation
- Rollback rules
- Checked vs unchecked exception
- `readOnly`
- Concurrency
- Duplicate check-in
- Idempotency

Attendance là nghiệp vụ có khả năng xảy ra request đồng thời hoặc duplicate request, vì vậy phải cân nhắc database constraint và concurrency control.

Quyết định cho MVP:

- Một transaction bao gồm ghi nhận idempotency, khóa/kiểm tra assignment hoặc session, ghi event/correction, cập nhật session/daily và lưu kết quả trả về. Lỗi ở bất kỳ bước ghi nào phải rollback toàn bộ.
- Dùng unique constraint kết hợp row lock hoặc optimistic locking để bảo vệ trạng thái. Khi session chưa tồn tại, khóa assignment hoặc dùng thao tác insert có constraint; không chỉ kiểm tra tồn tại bằng Java rồi insert.
- Hai check-in với key khác nhau cho cùng ca chỉ có một thao tác thành công; thao tác còn lại nhận conflict. Hai request cùng key/cùng nội dung nhận cùng kết quả thành công đã lưu, kể cả khi đến đồng thời.
- Check-out và correction cũng phải chống cập nhật đồng thời; correction từ dữ liệu cũ phải trả conflict để người dùng tải lại.
- Gửi notification, gọi thiết bị hoặc dịch vụ ngoài không nằm trong transaction ghi công. Bổ sung cơ chế giao nhận đáng tin cậy ở release có nghiệp vụ tương ứng.

---

## 19. AWS / Deployment

MVP có thể chạy bằng Docker trên một máy chủ với PostgreSQL. Lựa chọn AWS không phải điều kiện để hoàn thành chức năng chấm công.

Nếu dùng AWS, phân biệt luồng triển khai:

```text
Build Docker image
   ↓
AWS ECR
   ↓
AWS ECS / Fargate
```

và luồng request:

```text
Client → HTTPS / ALB → Application trên ECS / Fargate → PostgreSQL / RDS
```

Khi chọn Fargate, VPC, subnet và security group phải được cấu hình ngay từ đầu; VPC không phải hạng mục chờ scale mới bổ sung. Xem [AWS Fargate task networking](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/fargate-task-networking.html).

Trước pilot có dữ liệu thật: cấu hình HTTPS, database không public, secrets ngoài source code, trusted proxy, backup/khôi phục đã thử, health check, log lỗi và audit. Log vận hành không chứa token hoặc dữ liệu nhân sự không cần thiết. Có thể dùng dịch vụ managed tương ứng khi triển khai AWS.

Chỉ bổ sung theo nhu cầu:

```text
VPC Endpoint
SQS
EventBridge
Redis
```

Không đưa tất cả AWS services vào hệ thống nếu chưa có nhu cầu.

---

## 20. Cách trả lời trong project này

Khi tôi hỏi về implementation, hãy:

1. Giải thích **business context** trước nếu cần.
2. Giải thích architecture.
3. Giải thích database/domain model.
4. Giải thích backend flow.
5. Đưa code hoàn chỉnh khi tôi yêu cầu code.
6. Giải thích từng phần quan trọng của code.
7. Nêu rõ transaction boundary.
8. Nêu rõ authentication/authorization nếu liên quan.
9. Nêu các edge cases.
10. Nếu có nhiều phương án, so sánh ưu/nhược điểm trước khi đề xuất.

Không bỏ qua các bước quan trọng chỉ để câu trả lời ngắn.

---

## 21. Quy tắc khi sửa code

Khi refactor hoặc sửa code hiện có:

- Chỉ thay đổi phần liên quan đến yêu cầu.
- Không tự ý thay đổi các giá trị không liên quan.
- Không tự ý đổi naming, font, UI, config hoặc business rule nếu chưa được yêu cầu.
- Giữ nguyên behavior hiện tại nếu thay đổi đó không nằm trong yêu cầu.
- Nếu cần thay đổi một phần khác để implementation hoạt động chính xác, phải nói rõ lý do.

---

## 22. Phase / Release Plan

Mục tiêu dài hạn là **Company HR / Employee Management Platform**. Thứ tự triển khai hiện tại ưu tiên hoàn thành và vận hành chấm công trước khi mở rộng HRM.

Các version dưới đây là mốc phạm vi dự kiến, chưa phải thông báo tính năng đã phát hành. Chỉ chuyển release khi tiêu chí hoàn thành của release hiện tại đạt; lịch phát hành được xác định sau khi có kế hoạch triển khai và nguồn lực.

| Phase | Release                         | Kết quả chính                                                        | Mức ưu tiên                        |
| ----- | ------------------------------- | -------------------------------------------------------------------- | ---------------------------------- |
| 1     | v0.1 — Attendance MVP           | Nhân viên chấm công qua mạng công ty; HR xem và điều chỉnh bảng công | Đang ưu tiên                       |
| 2     | v0.2 — Attendance mở rộng       | Ca qua đêm, nhiều ca/phiên, đối soát và chốt công                    | Sau MVP                            |
| 3     | v0.3 — Leave & Calendar         | Đơn nghỉ phép, số dư phép, ngày lễ và lịch tổng hợp                  | Sau khi chấm công ổn định          |
| 4     | v0.4 — Device Integration       | Kết nối thiết bị thực tế và đồng bộ công đáng tin cậy                | Theo thiết bị doanh nghiệp sử dụng |
| 5     | v0.5 — HR & Employee Experience | Hồ sơ/cơ cấu tổ chức mở rộng, thông báo và báo cáo quản trị          | Sau các nghiệp vụ cốt lõi          |
| 6     | v1.x — HR mở rộng               | Payroll, Recruitment, Performance theo từng dự án nghiệp vụ          | Khi có nhu cầu được xác nhận       |

### Phase 1 — v0.1: Attendance MVP

**Mục tiêu:** hoàn thành luồng sử dụng hằng ngày từ đăng nhập, chấm công đến HR kiểm tra và xử lý sai sót.

Phạm vi bắt buộc:

- Đăng nhập/đăng xuất, quản lý tài khoản, Employee active/inactive và quyền Employee/Manager/HR/Admin theo mục 13.
- Nhân viên, phòng ban và quản lý trực tiếp ở mức tối thiểu để phân quyền và lọc bảng công.
- Mẫu ca trong ngày, ngày làm việc, ngày ngoại lệ, phân ca cho nhân viên, timezone và snapshot rule. Mỗi nhân viên tối đa một ca/ngày và một phiên vào/ra cho ca đó.
- Corporate network provider dùng allowlist IP/CIDR, trusted proxy và cấu hình địa điểm; có kiểm chứng đường mạng thực tế trước pilot.
- Check-in/check-out, trạng thái hôm nay, lịch sử cá nhân và lịch làm việc cá nhân trên desktop/mobile.
- Lưu event, session và daily summary; chống thao tác trùng và cập nhật đồng thời; hiển thị đi muộn, về sớm, thiếu check-out và không có ghi nhận.
- HR xem/lọc bảng công theo ngày, nhân viên, phòng ban; export CSV theo quyền và giới hạn khoảng ngày.
- HR bổ sung/sửa/vô hiệu hóa lần ghi nhận bằng correction có audit và tính lại dữ liệu liên quan. Nhân viên liên hệ HR khi cần sửa; workflow gửi/duyệt yêu cầu chưa nằm trong MVP.
- Migration database, triển khai pilot, log/audit, backup và khôi phục theo mục 19.

Thứ tự thực hiện trong release:

1. Chốt rule MVP trong tài liệu, thiết lập Spring Boot/PostgreSQL/migration và identity/access.
2. Hoàn thành Employee, phòng ban/quản lý tối thiểu và phân ca.
3. Hoàn thành check-in/check-out xuyên suốt từ web tới database, gồm xác thực mạng, transaction và idempotency.
4. Hoàn thành lịch sử, bảng công, correction và export.
5. Kiểm thử tích hợp, thử trên mạng triển khai thực tế và nghiệm thu pilot.

Tiêu chí hoàn thành:

- Nhân viên active có ca hợp lệ chấm công được qua mạng đã cấu hình; ngoài mạng, ngoài cửa sổ hoặc không có quyền bị từ chối với lý do rõ ràng.
- Thử giả forwarded headers không vượt qua kiểm tra mạng; hành vi VPN đã được kiểm chứng và ghi nhận theo mục 4.
- Double-click, retry sau timeout và hai request đồng thời không tạo hai check-in/check-out cho cùng phiên; retry cùng key trả kết quả đã lưu.
- Event/session/daily/idempotency nhất quán sau lỗi giữa transaction; lịch sử, báo cáo và CSV cho cùng một kết quả.
- Đi muộn, về sớm, giờ nghỉ, không có event, thiếu check-out và check-out không có check-in được xử lý theo rule. UI không biến dữ liệu chưa đủ thành công đã chốt.
- Correction giữ được dữ liệu gốc và lý do, không ghi đè thay đổi đồng thời, cập nhật đúng kết quả tổng hợp và không cho tự sửa công.
- Thay đổi lịch tương lai không thay kết quả quá khứ; nhân viên/manager không xem hoặc export được dữ liệu ngoài phạm vi.
- Validation từ chối ca qua đêm/nhiều ca trong MVP; giao diện responsive hoạt động cho toàn bộ luồng chính.
- Có kiểm thử tích hợp với PostgreSQL cho transaction/concurrency/constraint và kiểm thử authorization; đã thử khôi phục backup trên môi trường riêng trước pilot có dữ liệu thật.

Ngoài phạm vi v0.1: ca qua đêm, nhiều ca/ngày, nhiều lần ra/vào, break events, workflow duyệt sửa công, Leave, tính phép, Holiday module, Calendar tổng hợp, overtime được duyệt, tính lương, thiết bị sinh trắc học, notification service, multi-tenant và microservices.

### Phase 2 — v0.2: Attendance mở rộng và đối soát

**Điều kiện bắt đầu:** v0.1 đã được nghiệm thu pilot; các lỗi ảnh hưởng tính đúng của dữ liệu được xử lý trước khi thêm tính năng.

Phạm vi:

- Ca qua đêm, nhiều ca/ngày và nhiều phiên vào/ra; migration bỏ giới hạn một assignment/ngày và một session/assignment, thay bằng ràng buộc không chồng lấn và tối đa một phiên mở cho nhân viên tại một thời điểm. Giữ daily summary là tổng hợp theo ngày công.
- Gán `work_date` theo ngày bắt đầu ca; chống ca/phiên chồng lấn, xác định ca rõ ràng khi các cửa sổ chấm công giao nhau.
- Break events nếu doanh nghiệp cần ghi nhận nghỉ thực tế; không trừ hai lần với giờ nghỉ cố định.
- Nhân viên gửi yêu cầu bổ sung/sửa công; Manager/HR duyệt theo quyền, không tự duyệt. Khi duyệt mới áp dụng correction.
- Đối soát và khóa kỳ công; mở lại kỳ phải có quyền, lý do và audit. Correction vào kỳ đã khóa phải qua luồng mở lại.
- Ghi nhận và phê duyệt overtime theo rule doanh nghiệp; tách thời gian có mặt ngoài ca khỏi overtime được công nhận.

Tiêu chí hoàn thành:

- Ca `22:00–06:00` có check-out ngày sau nhưng thuộc đúng ngày công; nhiều ca/phiên không bị ghép nhầm hoặc tính trùng.
- Grace period, break, phần giao với lịch và overtime cho kết quả nhất quán ở biên thời gian.
- Duyệt yêu cầu lặp lại không tạo correction trùng; từ chối hoặc hủy yêu cầu chưa duyệt không sửa dữ liệu công.
- Kỳ đã khóa không bị thay đổi bởi API trực tiếp hoặc tác vụ tính lại; mở lại có lịch sử truy vết.
- Migration và tính lại có kiểm chứng không làm đổi dữ liệu v0.1 ngoài phạm vi được yêu cầu.

### Phase 3 — v0.3: Leave, Holiday và Company Calendar

**Mục tiêu:** nối lịch nghỉ với lịch làm việc và kết quả chấm công.

Phạm vi:

- Leave type, đơn nghỉ, duyệt/từ chối/hủy, bàn giao người duyệt và nghỉ một phần ngày.
- Chính sách cấp phép, sổ biến động phép và số dư; quy tắc chuyển kỳ/hết hạn được xác định theo chính sách doanh nghiệp.
- Ngày lễ và lịch theo công ty/địa điểm; thay thế thao tác nhập ngày ngoại lệ thủ công khi phù hợp, không áp dụng hồi tố ngầm.
- Calendar tổng hợp lịch làm việc, ngày lễ, nghỉ được duyệt và overtime đã có ở v0.2.
- Cập nhật đánh giá công khi duyệt/hủy phép; thay đổi tác động kỳ công đã khóa phải qua đối soát/mở lại.
- Thông báo trong ứng dụng cho việc cần duyệt và kết quả xử lý; có retry và chống gửi trùng nếu xử lý bất đồng bộ.

Tiêu chí hoàn thành:

- Ngày lễ không tạo leave request hoặc trừ phép; thời gian nghỉ được tính theo lịch áp dụng.
- Đơn chồng lấn, nghỉ nửa ngày/theo giờ, duyệt đồng thời, hủy đơn và hoàn phép không làm sai số dư.
- Người duyệt đúng phạm vi và không tự duyệt; đổi quản lý không để đơn bị kẹt hoặc lộ dữ liệu cho quản lý cũ.
- Calendar bảo vệ lý do nghỉ/tài liệu riêng tư; Leave, số dư và Attendance nhất quán sau duyệt/hủy.

### Phase 4 — v0.4: Device Integration

**Mục tiêu:** thêm nguồn ghi nhận từ thiết bị vào luồng Attendance đã có. Corporate network đã hoàn thành từ v0.1.

Phạm vi:

- Device registry, xác thực integration, ánh xạ mã nhân viên trên thiết bị với Employee.
- Triển khai một adapter cho thiết bị/vendor thực tế trước; chỉ thêm RFID, fingerprint hoặc face adapter khi có yêu cầu và thiết bị kiểm thử.
- Nhận batch/offline events, chống trùng, lưu dữ liệu nhận để đối soát, xử lý sai thứ tự và thời gian thiết bị lệch.
- Đối soát event tới muộn với ca, correction và kỳ đã khóa; không ghi đè công đã chốt.
- Chỉ nhận dữ liệu chấm công cần thiết; không mặc định lưu ảnh hoặc mẫu sinh trắc học trong HRM.

Tiêu chí hoàn thành:

- Gửi lại một event/batch không nhân đôi công; event chưa ánh xạ hoặc không hợp lệ có trạng thái và đường xử lý rõ ràng.
- Event tới muộn/sai thứ tự được đối soát đúng, giữ riêng thời điểm thực tế và thời điểm nhận.
- Provider mới dùng chung rule Attendance, không đưa logic vendor vào domain.
- Kiểm thử end-to-end bằng thiết bị hoặc môi trường vendor tương ứng; lỗi đồng bộ có thể retry và truy vết.

### Phase 5 — v0.5: HR và Employee Experience

Phạm vi:

- Hồ sơ nhân viên mở rộng, chức danh, hợp đồng và lịch sử thay đổi tổ chức/quản lý có ngày hiệu lực.
- Cơ cấu phòng ban/team nhiều cấp và chính sách phạm vi quản lý tương ứng.
- Dashboard, báo cáo quản trị và notification đa kênh theo nhu cầu; kế thừa báo cáo/thông báo tối thiểu của các release trước.

Tiêu chí hoàn thành:

- Chuyển phòng ban, quản lý hoặc trạng thái làm việc không làm mất lịch sử và không mở rộng quyền ngoài chính sách.
- Báo cáo đối soát được với Attendance/Leave, áp dụng đúng phạm vi dữ liệu.
- Notification có trạng thái gửi, retry và chống trùng; lỗi gửi không làm rollback nghiệp vụ đã hoàn tất.

### Phase 6 — v1.x: Mở rộng nghiệp vụ HR

Payroll, Recruitment và Performance là các nhánh nghiệp vụ riêng, mỗi nhánh phải có phân tích, release scope và tiêu chí nghiệm thu trước khi triển khai. Không mặc định đưa tất cả vào cùng một release.

Payroll chỉ bắt đầu khi rule tính lương được xác nhận và nguồn công/phép đã đối soát, khóa kỳ; thay đổi hồi tố phải có cơ chế điều chỉnh rõ ràng. Recruitment/Performance được ưu tiên theo nhu cầu thực tế sau đó.

### Scale và hạ tầng — theo bằng chứng vận hành

Scale là công việc xuyên suốt theo nhu cầu, không phải phase bắt buộc phải chuyển sang microservices. Chỉ bổ sung queue, cache, event-driven processing hoặc tách service khi có số liệu tải, yêu cầu độ tin cậy hoặc nhu cầu triển khai độc lập. Giữ Modular Monolith nếu vẫn đáp ứng được sản phẩm.
