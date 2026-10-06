# Company HRM — Frontend

Vue 3 + JavaScript + Vite, Vue Router, ESLint và Prettier. Giao diện doanh nghiệp
bằng tiếng Việt, responsive cho desktop/mobile, kết nối Auth, Employee và luồng cấp tài khoản bất đồng bộ qua Kafka.

## Chạy local

Dùng Node.js 24 LTS và npm:

```sh
cd frontend
nvm use # nếu sử dụng nvm
npm ci
npm run dev
```

Mở <http://localhost:5173>. Cổng cố định; nếu cổng đã bận, sử dụng server đang chạy
hoặc chỉ định cổng khác với `npm run dev -- --port 5175`.

Backend Auth phải đang chạy để đăng nhập. Cấu hình database, RSA keys, tài khoản
local và cách khởi động nằm trong [hướng dẫn Auth](../back-end/modules/auth-service-main/docs/hrm-auth.md).
Frontend không tự seed tài khoản hoặc thay đổi database.

Vite chuyển toàn bộ `/api` tới **API Gateway `http://localhost:8080`**, giữ nguyên
đường dẫn. Gateway chuyển tiếp tới Auth `8081`, Employee `8082`, Calendar `8083` và Leave `8084`,
bao gồm health-check của từng service. Chạy gateway theo
[hướng dẫn Gateway](../back-end/modules/gateway/README.md).

```sh
cp .env.example .env.local
```

Chỉ cần cấu hình `API_PROXY_TARGET` trỏ tới gateway rồi khởi động lại dev server.
Các biến `EMPLOYEE_API_PROXY_TARGET`, `CALENDAR_API_PROXY_TARGET` và
`VITE_API_BASE_URL` cũ không còn được sử dụng; có thể xóa khỏi `.env.local`.
`.env.local` không được commit.
Không đặt secret trong biến `VITE_*` vì chúng được đưa vào mã phía browser.

## Trang và quyền truy cập

| Trang                   | Đường dẫn                                              | Quyền                     |
| ----------------------- | ------------------------------------------------------ | ------------------------- |
| Đăng nhập               | `/login`                                               | Chưa đăng nhập            |
| Tổng quan               | `/`                                                    | Mọi tài khoản đã xác thực |
| Tài khoản của tôi       | `/account`                                             | Mọi tài khoản đã xác thực |
| Tạo tài khoản           | `/accounts/new`                                        | HR **hoặc** ADMIN         |
| Nhân viên               | `/employees`, `/employees/new`, `/employees/:id`       | Mọi tài khoản đã xác thực |
| Phòng ban               | `/departments`, `/departments/new`, `/departments/:id` | Mọi tài khoản đã xác thực |
| Chức danh               | `/positions`, `/positions/new`, `/positions/:id`       | Mọi tài khoản đã xác thực |
| Cấp tài khoản qua Kafka | Trong hồ sơ `/employees/:id`                           | HR **hoặc** ADMIN         |
| Kết nối dịch vụ         | `/services`                                            | Mọi tài khoản đã xác thực |
| Nghỉ phép của tôi       | `/leave`                                               | Mọi tài khoản đã xác thực |
| Duyệt nghỉ phép         | `/leave/inbox`                                         | HR hoặc ADMIN |
| Chi tiết đơn nghỉ       | `/leave/requests/:id`                                  | Người gửi hoặc HR/ADMIN (backend kiểm tra) |
| Từ chối truy cập        | `/forbidden`                                           | Tài khoản đã xác thực     |

- Bảo vệ cả điều hướng từ menu và truy cập URL trực tiếp; chưa đăng nhập được đưa
  về login rồi quay lại trang hợp lệ sau khi xác thực.
- Lấy vai trò hiện tại từ `/me` khi khôi phục phiên và mỗi lần điều hướng trang được
  bảo vệ. Không suy quyền từ JWT hoặc dữ liệu vai trò lưu ở browser.
- Hỗ trợ tài khoản nhiều vai trò; không có role hierarchy tự động. ADMIN không được
  suy thành HR. Quyền phía frontend phục vụ UX; backend vẫn kiểm tra từng API.
- Không có đăng ký công khai. Form cấp tài khoản yêu cầu Employee UUID hiện có theo
  contract của Auth, chỉ gọi `/auth/register`, không tạo/sửa/tìm kiếm Employee.
- Chưa triển khai danh sách tài khoản, sửa vai trò,
  khóa tài khoản hay quên mật khẩu. UI không giả lập các API chưa có.

## Nghỉ phép

Luồng Leave dùng `/api/v1/leave/requests`: nhân viên tạo đơn nghỉ cả/nửa ngày, HR/ADMIN
duyệt hoặc từ chối; người gửi được rút đơn đang chờ. UI gửi version qua `If-Match`,
yêu cầu tải lại khi có xung đột, giữ nội dung form khi lỗi. Leave có số dư phép năm;
Attendance đọc phép qua API để đối soát. Chưa có email hoặc tự đồng bộ ngày công đã snapshot. Xem [Leave Service](../back-end/modules/leave-service/README.md).

## Chấm công

| Trang | Đường dẫn | Quyền |
| --- | --- | --- |
| Công của tôi, ghi giờ vào/ra, lịch sử | `/attendance` | Đã đăng nhập |
| Mẫu ca, tạo/sửa/vô hiệu hóa, lịch sử | `/attendance/shifts` | HR/ADMIN |
| Phân công, chọn nhân viên/phạm vi, preview và lịch sử | `/attendance/schedules` | HR/ADMIN |
| Bảng công, đối soát phép/lịch, CSV | `/attendance/reports` | HR/ADMIN |

UI gọi `/api/v1/attendance` qua Gateway với phiên Auth hiện tại. Nhân viên chỉ tự
chấm công, giờ lấy từ server; HR áp lịch mặc định hoặc lịch riêng có ngày hiệu lực.
Phiên bản cũ yêu cầu tải lại; sửa nội dung phân công phải preview lại. Retry ghi công
giữ Idempotency-Key trong sessionStorage. CSV tải qua API có xác thực, hỗ trợ lỗi JSON.

Cần chạy Attendance, Employee, Leave, Calendar và áp schema Attendance. HR phải
phân ca trước, cấu hình allowlist/trusted proxy để chấm công local/triển khai.
Xem [hướng dẫn backend](../back-end/modules/attendance-service/README.md).
Bảng công/CSV tạm tính, chưa có khóa kỳ hoặc điều chỉnh giờ thủ công. Phép thay đổi
sau khi check-in cần HR cập nhật coverage để đối soát. Chưa hỗ trợ giờ linh hoạt.

Kiểm thử UI: `npm test`, `npm run build`,
`npx playwright test tests/e2e/attendance.spec.js`. Browser tests mock API để kiểm tra
UI và contract request; không khởi động cả hệ thống backend.

## API Auth được tích hợp

Prefix: `/api/v1/auth`.

| Endpoint           | Sử dụng                                              |
| ------------------ | ---------------------------------------------------- |
| `POST /login`      | Lấy cặp token từ `data`, sau đó đọc `/me`            |
| `GET /me`          | Thông tin account và tập `roles` hiện tại            |
| `POST /refresh`    | Xoay vòng cặp token khi khôi phục phiên hoặc gặp 401 |
| `POST /logout`     | Thu hồi refresh session hiện tại                     |
| `POST /logout-all` | Thu hồi tất cả refresh session sau xác nhận trong UI |
| `POST /register`   | Cấp tài khoản với email, password, employeeId, roles |

API client dùng timeout 15 giây; hiển thị lỗi sai mật khẩu, tài khoản inactive,
trùng email/UUID, 403, giới hạn đăng nhập (429 + Retry-After) và lỗi kết nối.
Request 401 chỉ được thử lại một lần sau refresh. Các request đồng thời dùng chung
một lần refresh để tránh sử dụng lại refresh token đã xoay vòng. Không tự retry
register khi lỗi mạng/5xx vì thao tác ghi có thể đã được server xử lý.

## Employee và cấp tài khoản qua Kafka

- Nhân viên: danh sách phân trang, xem chi tiết, tạo mới, sửa hồ sơ (`GET`, `POST`,
  `PUT /api/v1/employees` và `/:id`), cập nhật riêng trạng thái công việc
  (`PATCH /api/v1/employees/:id/status`).
- Phòng ban/chức danh: danh sách phân trang, chi tiết, tạo, sửa qua
  `/api/v1/departments` và `/api/v1/positions`. Không có thao tác xóa vì backend chưa hỗ trợ.
- Biểu mẫu nhân viên cho chọn phòng ban, chức danh, người quản lý; lựa chọn tải theo
  từng trang 100 bản ghi, có nút tải thêm. Trường tùy chọn gửi `null` khi để trống.
- Quyền CRUD nhân sự hiện là mọi tài khoản đã xác thực, đúng SecurityConfig của
  Employee. Riêng yêu cầu cấp tài khoản giới hạn HR/ADMIN theo AccountRequestService.
- Trong hồ sơ nhân viên, `POST /api/v1/employees/:id/account-requests` gửi email và
  UUID `Idempotency-Key`. Yêu cầu nhận `202` được theo dõi bằng
  `GET /api/v1/employees/:id/account-requests/:requestId` mỗi 3 giây, tối đa 30 lần.
  Dừng theo dõi khi có kết quả, gặp lỗi hoặc rời trang; có nút kiểm tra thủ công.
- Khi mất phản hồi POST, nút thử lại giữ nguyên email và mã gửi. Metadata yêu cầu
  lưu trong sessionStorage theo tài khoản và nhân viên để tiếp tục khi reload cùng tab;
  không tự gửi lại POST khi reload. Có thể tra cứu UUID yêu cầu đã biết từ bên ngoài.
- Hiển thị PENDING/SUCCEEDED/FAILED, lỗi trùng email/tài khoản, trạng thái tài khoản
  NOT_CREATED/PENDING_ACTIVATION/ACTIVE/DISABLED. Backend chưa có API liệt kê lịch sử
  yêu cầu, đặt mật khẩu/kích hoạt hoặc quản trị Kafka Connect riêng.
- Trang `/services` dùng hai endpoint `/api/v1/auth/health-check` và
  `/api/v1/employees/health-check`. Kết quả không khẳng định Kafka healthy.
- Cần chạy Kafka, migration và bật `HRM_EVENTS_ENABLED=true` ở cả hai service theo
  [hướng dẫn provisioning](../back-end/modules/infra/kafka/ACCOUNT-PROVISIONING.md).
  Không đặt địa chỉ broker trong frontend; browser chỉ gọi API Employee.

## Phiên đăng nhập

Access token và thông tin người dùng chỉ nằm trong bộ nhớ của mỗi tab. Refresh
token và mã phiên đăng nhập lưu chung trong `localStorage` tại `company-hrm.session`.
Các tab cùng origin (giao thức/host/port), cùng browser profile dùng chung phiên;
tab mới tự khôi phục đăng nhập. Không lưu mật khẩu, access token hoặc roles vào storage.
Phiên có thể được khôi phục cả khi đóng/mở lại trình duyệt, đến khi refresh token hết
hạn hoặc đăng xuất. Đăng xuất ở một tab xóa phiên ở tất cả tab qua sự kiện `storage`.

Web Locks tuần tự hóa login, refresh và logout giữa các tab. Mỗi lần refresh đọc token
mới nhất sau khi lấy khóa; mỗi tab vẫn giữ access token riêng. Mã phiên không đổi
khi refresh và đổi khi đăng nhập lại, giúp loại bỏ response từ phiên cũ. Frontend
kiểm tra lại phiên trước/sau request để xử lý cả tab nền nhận sự kiện storage chậm.
Yêu cầu HTTPS (hoặc localhost khi phát triển) và trình duyệt hỗ trợ Web Locks;
thiếu hỗ trợ sẽ báo lỗi thay vì refresh đồng thời không có khóa.

Sau khi nâng cấp từ phiên bản chỉ dùng `sessionStorage`, đăng nhập lại một lần.
Token riêng của tab cũ được xóa; không tự nhập lại token cũ vì nó có thể đã bị xoay
vòng hoặc thu hồi. Các tab đang chạy bản frontend cũ nên được tải lại.

Backend hiện trả refresh token trong JSON, chưa hỗ trợ HttpOnly cookie. Vì vậy
refresh token trong localStorage vẫn có thể bị đọc nếu xảy ra XSS và tồn tại sau
khi đóng tab. Khi chuyển sang cookie HttpOnly cần điều chỉnh cả backend và cơ chế CSRF.

Lỗi mạng khi khôi phục phiên đưa tới trang kết nối gián đoạn để thử lại, không tự
xóa phiên. Token refresh đã hết hạn/thu hồi hoặc account inactive đưa về login.
Nếu refresh đã được backend xử lý nhưng response bị mất, lần thử sau có thể phải
đăng nhập lại do rotation của backend.

Logout hiện tại luôn xóa credentials local; nếu không xác nhận được thu hồi trên
server, UI thông báo rõ. Logout-all thất bại giữ phiên hiện tại để người dùng thử
lại. Backend chỉ thu hồi refresh session; access JWT đã phát hành có thể còn hiệu
lực tới khi hết hạn (hiện cấu hình 15 phút).

## Cấu trúc

```text
src/
├── auth/                   # HTTP client, session và vai trò truy cập
├── assets/styles/main.css  # Design system và responsive
├── components/             # Icon và role badge dùng chung
├── layouts/                # Sidebar, topbar và workspace layout
├── router/                 # Routes và guard xác thực/phân quyền
├── views/                  # Login, dashboard, account, create account, lỗi
├── App.vue
└── main.js
tests/
├── unit/                   # Phiên, rotation, concurrency, redirect an toàn
└── e2e/                    # Browser: đăng nhập, quyền, mobile, live Auth opt-in
```

## Kiểm tra và build

```sh
npm run lint
npm run format:check
npm run test
npm run build
npx playwright install chromium
npm run test:e2e
```

- `npm run build` biên dịch Vue SFC và JavaScript thành bản production trong `dist/`.
- `npm run test` dùng Vitest; `npm run test:watch` để phát triển.
- E2E mặc định giả lập contract Auth, Employee và provisioning, không ghi vào database.
  Bao gồm tạo/sửa hồ sơ, đổi trạng thái, danh mục, phân trang, lỗi kết nối, phân quyền,
  retry cùng idempotency key, tiếp tục theo dõi sau reload và responsive mobile. Playwright chạy
  Vite riêng trên cổng 5174, không dùng dev server 5173.
- Live Auth test mặc định bỏ qua. Để thử với backend thật, đặt `HRM_LIVE_AUTH=1`,
  `HRM_TEST_EMAIL`, `HRM_TEST_PASSWORD` của tài khoản local, rồi chạy
  `npm run test:e2e -- tests/e2e/live-auth.spec.js`. Test đăng nhập, đọc account,
  refresh, kiểm tra quyền và logout; không gọi register hoặc logout-all. Login
  vẫn cập nhật lastLoginAt và tạo session theo hành vi backend.
- `npm run format` format mã; `npm run lint:fix` sửa lỗi lint hỗ trợ tự động.
- `npm run preview` xem build local ở <http://localhost:4173>.

## Triển khai

Phục vụ `dist/` bằng web server. Vue Router dùng HTML5 history: fallback về
`index.html` cho route frontend. Cấu hình reverse proxy `/api/v1/auth/*` tới Auth và các prefix
`/api/v1/employees`, `/api/v1/departments`, `/api/v1/positions` tới Employee, không fallback API về `index.html`. Dùng HTTPS khi triển khai thực tế.
Vite dev proxy không thay thế reverse proxy production; `npm run preview` chỉ để
xem thử build local.

Tham khảo [Vue Router navigation guards](https://router.vuejs.org/guide/advanced/navigation-guards.html)
và [Vue state management](https://vuejs.org/guide/scaling-up/state-management.html).

## Lịch nghỉ và sự kiện

Trang `/calendar` lấy toàn bộ dữ liệu từ `GET /api/v1/calendar?year=...` với Bearer access token qua phiên đăng nhập. Mọi tài khoản đã xác thực đều được xem, bao gồm EMPLOYEE; token hết hạn được refresh theo cơ chế chung.
`src/calendar/api.js` gọi API, `constants.js` chứa nhãn tiếng Việt cho enum API,
`utils.js` tính ô lịch và định dạng ngày theo `Asia/Ho_Chi_Minh`.
Giao diện dùng trực tiếp `availableYears`, `title`, `type`, `holidayKind`,
`description`, `location` và `status` từ response, không dùng dữ liệu mẫu dự phòng.
Sự kiện cả ngày bao gồm ngày kết thúc; sự kiện theo giờ không bao gồm thời điểm kết thúc.
Sự kiện `CANCELLED` vẫn hiển thị kèm nhãn đã hủy.

Frontend gọi toàn bộ API cùng origin (qua Vite proxy khi chạy local).
Khi triển khai bản build, cấu hình reverse proxy `/api` tới **Gateway**.

Trang `/services` kiểm tra Auth, Employee và Calendar bằng access token của phiên
đăng nhập. `GET /api/v1/calendar/health-check` qua Gateway được chuyển thành
`GET /actuator/health` của Calendar Service; frontend đọc trạng thái tổng hợp
`status` (API không trả chi tiết database). `/actuator/health` tại Gateway chỉ là
trạng thái của chính gateway, không thay cho health-check Calendar.

### Quản lý lịch (HR/ADMIN)

Mở **Quản lý lịch** từ menu hoặc trang lịch nhân viên:

- `/calendar-events`: danh sách, lọc khoảng ngày (tối đa 366 ngày), loại, trạng thái và phân trang. Bản nháp lên trước, sau đó ngày bắt đầu gần hôm nay nhất theo giờ Việt Nam; backend sắp xếp trước phân trang.
- `/calendar-events/new`: tạo bản nháp.
- `/calendar-events/:id`: xem/sửa, công bố, hủy có lý do hoặc xóa bản nháp.

Mọi request quản lý dùng Bearer token. Khi sửa, công bố, hủy, xóa, UI gửi `If-Match`
khớp `version` đã đọc. Khi nhận `412`, giữ nội dung đang nhập và yêu cầu tải lại có xác nhận;
không tự lấy version mới rồi ghi đè. Không tự retry thao tác ghi khi lỗi mạng/5xx.
Sự kiện đã công bố không thay đổi loại, loại ngày nghỉ hoặc chế độ cả ngày;
sự kiện đã bắt đầu và đã hủy chỉ xem. Giờ nhập luôn theo Việt Nam (UTC+7), kể cả
khi trình duyệt dùng múi giờ khác. Nhân viên thường vẫn xem lịch tại `/calendar`.

Reverse proxy cần chuyển cả `/api/v1/calendar-events` và các đường dẫn con đến Calendar Service.
