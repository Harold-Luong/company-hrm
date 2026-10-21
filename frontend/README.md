# Company HRM — Frontend

Chạy bằng container riêng: dùng `Dockerfile`, `compose.yaml` và
`.env.docker.example` tại thư mục này. Xem [hướng dẫn Docker độc lập](../README.md#docker-independent)
để chuẩn bị network, cấu hình kết nối, build/start/stop riêng hoặc phát hành image.

**Mục lục**

- [Project chạy độc lập](#project-chạy-độc-lập)
- [Chạy local](#chạy-local)
- [Trang và quyền truy cập](#trang-và-quyền-truy-cập)
- [Nghỉ phép](#nghỉ-phép)
- [Chấm công](#chấm-công)
- [API Auth được tích hợp](#api-auth-được-tích-hợp)
- [Employee và cấp tài khoản qua Kafka](#employee-và-cấp-tài-khoản-qua-kafka)
- [Phiên đăng nhập](#phiên-đăng-nhập)
- [Cấu trúc](#cấu-trúc)
- [Kiểm tra và build](#kiểm-tra-và-build)
- [Triển khai](#triển-khai)
- [Lịch nghỉ và sự kiện](#lịch-nghỉ-và-sự-kiện)
- [Đi trễ / về sớm](#đi-trễ--về-sớm)
- [Đi trễ / về sớm — API đã kết nối](#đi-trễ--về-sớm--api-đã-kết-nối)

Vue 3 + JavaScript + Vite, Vue Router, ESLint và Prettier. Giao diện doanh nghiệp
bằng tiếng Việt, responsive cho desktop/mobile, kết nối Auth, Employee, Calendar, Leave và Attendance; theo dõi cấp tài khoản bất đồng bộ qua API Employee.

Badge **Duyệt nghỉ phép** và **Duyệt đi trễ / về sớm** lấy số đơn `PENDING` từ
`pending-count` của từng service. HR/ADMIN nhận sự kiện SSE từ Notification để cập nhật
cả hai badge khi có đơn mới, sửa, rút hoặc xét duyệt; thao tác trong tab cập nhật ngay.
Kết nối tự mở lại khi hết hạn/gián đoạn và vẫn kiểm tra mỗi 30 giây nếu Notification
chưa sẵn sàng. Realtime cần Kafka, topic, outbox và `HRM_NOTIFICATIONS_ENABLED=true`.

## Project chạy độc lập

Frontend có package.json, dependency và cấu hình Vite riêng. Chạy/build UI không
cần khởi động backend; đăng nhập và các màn hình lấy dữ liệu thật cần Gateway,
Auth và service tương ứng. Không chạy Maven từ frontend.

## Chạy local

Xem [hướng dẫn local chung](../README.md#local) để cấu hình `.env`, database và các backend. Vite tự đọc `.env`; `.env.local` ưu tiên hơn khi cùng tên biến.

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
local và cách khởi động nằm trong [hướng dẫn Auth](../back-end/modules/auth-service-main/README.md).
Frontend không tự seed tài khoản hoặc thay đổi database.

Vite chuyển toàn bộ `/api` tới **API Gateway `http://localhost:8080`**, giữ nguyên
đường dẫn. Gateway chuyển tiếp tới Auth `8081`, Employee `8082`, Calendar `8083`, Leave `8084`, Attendance `8085` và Notification `8086`,
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

Hai trang trợ giúp dùng chung cho mọi tài khoản đã đăng nhập, mở từ nhóm **Trợ giúp** trên menu:

- `/help/guide`: hướng dẫn theo tác vụ, quyền HR/ADMIN, câu hỏi thường gặp và liên kết mở chức năng.
- `/help/features`: danh mục chức năng có tìm kiếm không dấu, lọc theo nhóm; công thức công thường, làm tròn, thời gian thực tế, đi trễ/về sớm có phép, OT, số ngày phép, số dư và chỉ số báo cáo, kèm ví dụ.

Nội dung nằm trong `src/help/content.js`; cập nhật cùng các thay đổi quy tắc ở Attendance/Leave và `src/attendance/report.js`. Các trang chỉ giải thích quy tắc hiện có, không tính lại hay ghi dữ liệu nghiệp vụ. Liên kết tới trang quản lý chỉ hiện cho HR/ADMIN; nội dung tài liệu vẫn đọc được với mọi vai trò. Bảng công và phần chấm công trong hồ sơ có liên kết tra cứu công thức.

| Trang                   | Đường dẫn                                                          | Quyền                                      |
| ----------------------- | ------------------------------------------------------------------ | ------------------------------------------ |
| Đăng nhập               | `/login`                                                           | Chưa đăng nhập                             |
| Tổng quan               | `/`                                                                | Mọi tài khoản đã xác thực                  |
| Tài khoản của tôi       | `/account`                                                         | Mọi tài khoản đã xác thực                  |
| Tạo tài khoản           | `/accounts/new`                                                    | ADMIN                                      |
| Nhân viên               | `/employees`, `/employees/new`, `/employees/:id`                   | HR hoặc ADMIN                              |
| Phòng ban               | `/departments`, `/departments/new`, `/departments/:id`             | HR hoặc ADMIN                              |
| Chức danh               | `/positions`, `/positions/new`, `/positions/:id`                   | HR hoặc ADMIN                              |
| Cấp tài khoản qua Kafka | Trong hồ sơ `/employees/:id`                                       | ADMIN                                      |
| Kết nối dịch vụ         | `/services`                                                        | Mọi tài khoản đã xác thực                  |
| Nghỉ phép của tôi       | `/leave`                                                           | Mọi tài khoản đã xác thực                  |
| Duyệt nghỉ phép         | `/leave/inbox`                                                     | HR hoặc ADMIN                              |
| Chi tiết đơn nghỉ       | `/leave/requests/:id`                                              | Người gửi hoặc HR/ADMIN (backend kiểm tra) |
| Lịch nghỉ và sự kiện    | `/calendar`                                                        | Mọi tài khoản đã xác thực                  |
| Quản lý lịch            | `/calendar-events`, `/calendar-events/new`, `/calendar-events/:id` | HR hoặc ADMIN                              |
| Từ chối truy cập        | `/forbidden`                                                       | Tài khoản đã xác thực                      |

- Bảo vệ cả điều hướng từ menu và truy cập URL trực tiếp; chưa đăng nhập được đưa
  về login rồi quay lại trang hợp lệ sau khi xác thực.
- Lấy vai trò hiện tại từ `/me` khi khôi phục phiên và mỗi lần điều hướng trang được
  bảo vệ. Không suy quyền từ JWT hoặc dữ liệu vai trò lưu ở browser.
- Hỗ trợ tài khoản nhiều vai trò; không có role hierarchy tự động. Các nhóm quyền
  nghiệp vụ chấp nhận cả HR và ADMIN. ADMIN có thêm quyền quản trị tài khoản và cấp tài khoản:
  tạo trực tiếp và gửi yêu cầu cấp tài khoản cho nhân viên. Tra cứu kết quả, kiểm
  tra/gửi lại lời mời kích hoạt đều dành cho HR/ADMIN.
- MANAGER giữ nguyên role, chờ thiết kế sau; chưa có màn hình quản lý nhóm hoặc
  quyền xét duyệt riêng, chưa có luồng Manager duyệt rồi HR duyệt tiếp.
  Quyền phía frontend phục vụ UX; backend vẫn kiểm tra từng API và phạm vi dữ liệu.
- Không có đăng ký công khai. Form cấp tài khoản yêu cầu Employee UUID hiện có theo
  contract của Auth, chỉ gọi `/auth/register`, không tạo/sửa/tìm kiếm Employee.
- ADMIN quản trị tài khoản tại `/accounts`: lọc email/trạng thái/vai trò, xem chi tiết và audit,
  khóa/mở khóa, đổi vai trò, buộc đăng xuất. Mỗi thao tác yêu cầu lý do, xác nhận và phiên bản hiện tại;
  lỗi mạng hoặc 412 yêu cầu tải lại, không tự gửi lại thao tác ghi. Không tự sửa tài khoản của mình.
- Chưa triển khai đổi/quên mật khẩu.

## Nghỉ phép

Luồng Leave dùng `/api/v1/leave/requests`: nhân viên tạo đơn nghỉ cả/nửa ngày, HR/ADMIN
duyệt hoặc từ chối; người gửi được rút đơn đang chờ. UI gửi version qua `If-Match`,
yêu cầu tải lại khi có xung đột, giữ nội dung form khi lỗi. Leave có số dư phép năm;
Attendance đọc phép qua API và tự cập nhật coverage khi tải công kỳ mở. Chưa có email thông báo phép. Xem [Leave Service](../back-end/modules/leave-service/README.md).

Trong chi tiết đơn đã duyệt, chủ đơn có thể gửi **Điều chỉnh phép đã duyệt**: hủy toàn bộ hoặc chọn khoảng thời gian còn nghỉ ngắn hơn. HR/ADMIN tìm các yêu cầu qua bộ lọc **Điều chỉnh phép đã duyệt** tại `/leave/inbox`. Đơn và số dư chỉ đổi khi HR/ADMIN khác duyệt; phép năm được hoàn phần chênh lệch, lịch cá nhân cập nhật qua SSE/polling. Có lịch sử điều chỉnh, lý do từ chối và rút yêu cầu đang chờ. Badge duyệt nghỉ phép tính cả các yêu cầu điều chỉnh.

## Chấm công

| Trang                                                 | Đường dẫn                    | Quyền                                |
| ----------------------------------------------------- | ---------------------------- | ------------------------------------ |
| Công của tôi, ghi giờ vào/ra, lịch sử                 | `/attendance`                | Đã đăng nhập                         |
| Đơn đi trễ/về sớm cá nhân                             | `/attendance/requests`       | Đã đăng nhập                         |
| Duyệt đơn đi trễ/về sớm                               | `/attendance/requests/inbox` | HR/ADMIN                             |
| Bổ sung/điều chỉnh công                               | `/attendance/corrections`    | Đã đăng nhập; xét duyệt cần HR/ADMIN |
| Đăng ký/chấm giờ OT                                   | `/attendance/overtime`       | Đã đăng nhập; xét duyệt cần HR/ADMIN |
| Mẫu ca, tạo/sửa/vô hiệu hóa, lịch sử                  | `/attendance/shifts`         | HR/ADMIN                             |
| Phân công, chọn nhân viên/phạm vi, preview và lịch sử | `/attendance/schedules`      | HR/ADMIN                             |
| Bảng công, đối soát phép/lịch, CSV                    | `/attendance/reports`        | HR/ADMIN                             |

UI gọi `/api/v1/attendance` qua Gateway với phiên Auth hiện tại. Nhân viên chỉ tự
chấm công, giờ lấy từ server; HR áp lịch mặc định hoặc lịch riêng có ngày hiệu lực.
Phiên bản cũ yêu cầu tải lại; sửa nội dung phân công phải preview lại. Retry ghi công
giữ Idempotency-Key trong sessionStorage. CSV tải qua API có xác thực, hỗ trợ lỗi JSON. Bản xuất mặc định gồm 16 cột tiếng Việt
về nhân viên/ngày, giờ vào/ra GMT+7, công, phép, trễ/sớm, OT, cảnh báo đối soát và
trạng thái tạm tính; không chứa UUID, phiên bản và thời điểm đồng bộ. Ô công chưa
xác định để trống. Bản CSV đầy đủ chỉ lấy qua API với `detail=true`.

Trang Bảng công HR/ADMIN tổng hợp **mỗi nhân viên một dòng** theo kỳ đã tải:
giờ tính công, giờ phép năm/nghỉ không lương, phút trễ/sớm và OT ghi nhận/được duyệt.
Bốn chỉ số đầu trang luôn tính trên toàn phạm vi đã tải; bộ lọc tìm tên/mã,
cần kiểm tra, công chưa đầy đủ, trễ/sớm chỉ lọc danh sách nhân viên. Mặc định ưu tiên
người có nhiều ngày cần kiểm tra; bấm tên để xem từng ngày và cập nhật phép/lịch.
Quay lại giữ bộ lọc/phân trang; cập nhật ngày công tính lại tổng hợp ngay.

Tổng giờ chỉ cộng giá trị đã xác định từ API, không quy đổi một ngày thành 8 giờ.
Ngày thiếu/sai giờ, xung đột và đơn chờ duyệt được đánh dấu để kiểm tra; ca đang mở
hoặc chưa bắt đầu được ghi riêng, không tự coi là thiếu chấm công. Ngày không có ca
không tính là vắng mặt hoặc công chưa đầy đủ. Đơn chờ duyệt được đếm duy nhất theo
loại/ID dù trải nhiều ngày. Dữ liệu null hiện chưa xác định, không thay thành 0.
Nút **Xuất CSV theo ngày** vẫn xuất toàn bộ phạm vi đã tải; nếu sửa bộ lọc kỳ mà chưa
bấm **Xem bảng công**, UI nhắc áp dụng và CSV tiếp tục dùng kỳ đang hiển thị.

Cần chạy Attendance, Employee, Leave, Calendar và áp schema Attendance. Backend
tạo lịch mặc định thứ Hai–thứ Sáu nếu chưa có lịch công ty; HR kiểm tra lịch có
hiệu lực và cấu hình allowlist/trusted proxy để chấm công local/triển khai.
Xem [hướng dẫn backend](../back-end/modules/attendance-service/README.md).
Bảng công/CSV có trạng thái tạm tính hoặc đã chốt. HR/ADMIN chốt/mở tháng tại `/attendance/reports`. Đã có đơn bổ sung/điều chỉnh giờ và phê duyệt tại `/attendance/corrections`. Phép thay đổi
sau khi check-in được cập nhật khi tải công kỳ mở; kỳ đã chốt cần mở lại để đối soát. Chưa hỗ trợ giờ linh hoạt.

Kiểm thử UI: `npm test`, `npm run build`,
`npx playwright test tests/e2e/attendance.spec.js`. Browser tests mock API để kiểm tra
UI và contract request; không khởi động cả hệ thống backend.

## API Auth được tích hợp

Prefix: `/api/v1/auth`.

| Endpoint           | Sử dụng                                              |
| ------------------ | ---------------------------------------------------- |
| `GET /csrf`        | Lấy CSRF token và khởi tạo cookie CSRF HttpOnly     |
| `POST /login`      | Lấy access token từ `data`, nhận refresh cookie, đọc `/me` |
| `GET /me`          | Thông tin account và tập `roles` hiện tại            |
| `POST /refresh`    | Gửi cookie và CSRF header, nhận access token và cookie mới |
| `POST /logout`     | Thu hồi phiên hiện tại và xóa refresh cookie         |
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
- Các route quản lý nhân viên/phòng ban/chức danh và API tương ứng giới hạn HR/ADMIN.
  EMPLOYEE/MANAGER chỉ đọc hồ sơ bản thân qua trang tài khoản; backend kiểm tra `employee_id`.
  Chỉ ADMIN được gửi yêu cầu cấp tài khoản; HR/ADMIN vẫn được tra cứu kết quả và hỗ trợ kích hoạt.
- Trong hồ sơ nhân viên, `POST /api/v1/employees/:id/account-requests` gửi email và
  UUID `Idempotency-Key`. Yêu cầu nhận `202` được theo dõi trong layout chung qua
  SSE `account-results`, sau đó gọi `GET /api/v1/employees/:id/account-requests/:requestId`
  để xác nhận trạng thái Employee trước khi hiện toast. Trạng thái trong hồ sơ và
  danh sách nhân viên tự cập nhật mà không ghi đè nội dung form đang sửa.
  Polling dự phòng mỗi 3 giây, sau 30 lần giảm còn mỗi 30 giây; vẫn theo dõi khi
  chuyển trang, kể cả phản hồi POST đến sau khi rời form. Dừng khi hoàn tất/đăng xuất.
  Toast chỉ dành cho người yêu cầu và chống lặp theo requestId trong tab hiện tại.
  Tra cứu thủ công yêu cầu khác vẫn poll mỗi 3 giây, tối đa 30 lần và không tạo toast.
  Cần Auth phát `data.requestedBy`, bật Kafka và áp schema `002` theo
  [Notification](../back-end/modules/notification-service/README.md#bật-luồng-thông-báo).
- Khi mất phản hồi POST, nút thử lại giữ nguyên email và mã gửi. Metadata yêu cầu
  lưu trong sessionStorage theo tài khoản và nhân viên để tiếp tục khi reload cùng tab;
  không tự gửi lại POST khi reload. Có thể tra cứu UUID yêu cầu đã biết từ bên ngoài.
- Hiển thị PENDING/SUCCEEDED/FAILED, lỗi trùng email/tài khoản, trạng thái tài khoản
  NOT_CREATED/PENDING_ACTIVATION/ACTIVE/DISABLED. Backend chưa có API liệt kê lịch sử
  yêu cầu hoặc quản trị Kafka Connect riêng. Auth đã có API đặt mật khẩu/kích hoạt
  qua `POST /api/v1/auth/activate`. Trang Vue công khai `/activate?token=...` cho phép
  đặt/xác nhận mật khẩu và hiện nút **Đi đến đăng nhập** sau khi kích hoạt thành công.
  Trang không khởi tạo phiên đăng nhập; token chỉ giữ trong bộ nhớ và được xóa khỏi URL,
  vì vậy tải lại trang cần mở lại link email. Đặt `AUTH_ACTIVATION_FRONTEND_URL` ở Auth
  thành URL frontend (`http://localhost:5173/activate` khi chạy local). Nginx phục vụ
  Vue tại `/activate`, tắt access log và cache cho trang này; proxy `/api` tới Gateway.
  Khi dùng proxy khác, không ghi query token hoặc body API kích hoạt vào log.
  Xem [Auth](../back-end/modules/auth-service-main/README.md#activation).
- Trang `/services` kiểm tra health của Auth, Employee, Calendar, Leave, Attendance
  và Notification qua Gateway. Notification gọi `/api/v1/notifications/health-check`,
  Gateway chuyển tới `/actuator/health` của Notification để kiểm tra service/database.
  Kết quả không xác nhận việc truyền sự kiện Kafka hoặc SSE từ đầu đến cuối.
- Cần chạy Kafka, migration và bật `HRM_EVENTS_ENABLED=true` ở cả hai service theo
  [hướng dẫn provisioning](../back-end/modules/employee-service/README.md#provisioning).
  Không đặt địa chỉ broker trong frontend; browser chỉ gọi API Employee.

## Phiên đăng nhập

Mỗi tài khoản chỉ có một phiên trên server. Đăng nhập từ trình duyệt/thiết bị khác
thay thế phiên cũ; nhiều tab cùng browser profile vẫn dùng chung phiên. Khi nhận
401 và refresh cũng bị từ chối, UI xóa phiên và về login. Tab đang hiển thị kiểm tra
phiên mỗi 15 giây, khi được focus hoặc hiển thị lại; thông báo cho biết phiên đã
kết thúc hoặc tài khoản đăng nhập ở nơi khác. Lỗi mạng/503 giữ phiên để thử lại.
Backend chặn request mới của phiên cũ ngay khi kiểm tra; thời gian polling chỉ ảnh
hưởng thời điểm giao diện chuyển về login. Token cũ trước bản nâng cấp cần đăng nhập lại.

Access token và thông tin người dùng chỉ nằm trong bộ nhớ mỗi tab. Refresh token
nằm trong cookie `HttpOnly; Secure; SameSite=Strict` do Auth đặt. Frontend không
đọc cookie này, không nhận refresh token trong JSON, không lưu token vào storage.
`company-hrm.session` chỉ chứa `{ id }` để nhận biết thay đổi phiên giữa các tab.

Login, refresh, logout và logout-all lấy masked CSRF token qua
`GET /api/v1/auth/csrf` rồi gửi header `X-XSRF-TOKEN`. Cookie CSRF cũng HttpOnly;
frontend lấy token qua JSON và không lưu nó vào storage. Các request dùng
`credentials: 'same-origin'`; triển khai frontend và `/api` qua cùng origin.
Web Locks tuần tự hóa thao tác cookie giữa các tab; không tự retry refresh khi
kết quả không xác định. Bearer API khác tiếp tục dùng access token.

Lựa chọn production mặc định yêu cầu HTTPS. Với HTTP local, đặt
`AUTH_COOKIES_SECURE=false` ở Auth (cookie đổi sang tên dành cho dev), chỉ dùng
trên máy phát triển. Không dùng cấu hình này ở production. Gateway phải cho qua
`Set-Cookie`, cookie và CSRF header tới Auth; cookie không chuyển tới service khác.

Nâng cấp đồng thời frontend/Auth/Gateway. Người dùng phiên bản cũ cần đăng nhập
lại; frontend xóa refresh token cũ trong localStorage/sessionStorage và không gửi
lại token đó. Reload các tab đang chạy bản cũ. Cookie cho phép khôi phục sau khi
mở lại trình duyệt hoặc mất metadata trong storage, nếu phiên vẫn hợp lệ.

Đăng xuất đồng bộ các tab qua `storage`. Nếu mất mạng, frontend không thể tự xóa
cookie HttpOnly: xóa trạng thái local và lưu cờ `company-hrm.signed-out` để tránh
khôi phục cookie khi reload. Cờ này chỉ được bỏ sau đăng nhập mới thành công;
UI vẫn thông báo chưa xác nhận được thu hồi trên server.

Lỗi mạng khi khôi phục phiên đưa tới trang kết nối gián đoạn để thử lại, không tự
xóa phiên. Token refresh đã hết hạn/thu hồi hoặc account inactive đưa về login.
Nếu refresh đã được backend xử lý nhưng response bị mất, lần thử sau có thể phải
đăng nhập lại do rotation của backend.

Logout hiện tại luôn xóa credentials local; nếu không xác nhận được thu hồi trên
server, UI thông báo rõ. Logout-all thất bại giữ phiên hiện tại để người dùng thử
lại. Backend chỉ thu hồi refresh session; access JWT đã phát hành có thể còn hiệu
lực tới khi hết hạn (mặc định Auth hiện là 1 ngày; thời hạn thực tế theo cấu hình khi phát hành token).

## Cấu trúc

Frontend được tổ chức theo nghiệp vụ (`hrm`, `attendance`, `leave`, `calendar`),
với HTTP client dùng chung trong `src/api`, tiện ích trong `src/shared` và logic
Vue tái sử dụng trong `src/composables`. Views/components gọi API nghiệp vụ;
endpoint, payload và session được quản lý ở lớp tương ứng.

Xem [kiến trúc frontend và quy tắc mở rộng](docs/architecture.md) để biết nơi sửa
URL, thêm API, xử lý request đồng thời, idempotency và kiểm tra sau thay đổi.

Text giao diện được quản lý trong `src/locales/`, adapter ở `src/content/`, cấu hình dùng chung nằm trong
`src/config/`. Xem [hướng dẫn chỉnh sửa nội dung](src/content/README.md), gồm ví dụ
đổi nhãn nút theo trạng thái mà không sửa component.

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
`index.html` cho route frontend. Cấu hình reverse proxy `/api` tới Gateway để dùng chung định tuyến và kiểm tra JWT; không fallback API về `index.html`. Dùng HTTPS khi triển khai thực tế.
Vite dev proxy không thay thế reverse proxy production; `npm run preview` chỉ để
xem thử build local.

Tham khảo [Vue Router navigation guards](https://router.vuejs.org/guide/advanced/navigation-guards.html)
và [Vue state management](https://vuejs.org/guide/scaling-up/state-management.html).

## Lịch nghỉ và sự kiện

Trang `/calendar` kết hợp lịch chung từ `GET /api/v1/calendar?year=...` với nghỉ phép
cá nhân từ `GET /api/v1/leave/requests/calendar?year=...`, dùng Bearer access token.
API nghỉ phép luôn lấy `employee_id` từ token, kể cả HR/ADMIN; không nhận phạm vi
nhân viên khác từ trình duyệt. Token hết hạn được refresh theo cơ chế chung.
Đơn `PENDING` và `APPROVED` hiển thị riêng với nhãn trạng thái, cả ngày/buổi sáng/
buổi chiều và liên kết xem đơn. Đơn bị từ chối hoặc rút được loại khỏi lịch.
Không sao chép đơn cá nhân thành sự kiện chung hay sửa dữ liệu Calendar Service.
Lịch cập nhật qua SSE dùng chung kết nối với sidebar, sau thao tác trong tab,
và mỗi 30 giây để dự phòng. Khi Leave tạm lỗi, lịch chung vẫn dùng được và phần
nghỉ phép có thông báo tải lại. Chuyển năm/tài khoản xóa dữ liệu cá nhân cũ và
bỏ qua phản hồi đến muộn.
`src/calendar/api.js` gọi API, `constants.js` chứa nhãn tiếng Việt cho enum API,
`utils.js` tính ô lịch và định dạng ngày theo `Asia/Ho_Chi_Minh`.
Giao diện dùng các năm từ `availableYears`, bổ sung năm liền trước/sau để xem
phép ở năm chưa có sự kiện chung; dùng trực tiếp `title`, `type`, `holidayKind`,
`description`, `location` và `status` từ response, không dùng dữ liệu mẫu dự phòng.
Sự kiện cả ngày bao gồm ngày kết thúc; sự kiện theo giờ không bao gồm thời điểm kết thúc.
Sự kiện chung `CANCELLED` vẫn hiển thị kèm nhãn đã hủy.

Frontend gọi toàn bộ API cùng origin (qua Vite proxy khi chạy local).
Khi triển khai bản build, cấu hình reverse proxy `/api` tới **Gateway**.

Trang `/services` kiểm tra Auth, Employee, Calendar, Leave và Attendance bằng access token của phiên
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

## Đi trễ / về sớm

`/attendance/requests` đã dùng API thật: tải ca theo ngày, gửi/sửa/rút đơn, xem
trạng thái/lịch sử. HR/ADMIN có hàng chờ duyệt riêng tại `/attendance/requests/inbox`. Đơn duyệt ghi nhận phần
có phép; công vẫn trừ theo bước 15 phút. Không còn dữ liệu mẫu hoặc memory store.
[Contract và migration cần chạy](#requests).

### Phân ca và OT

- `/attendance/schedules`: backend tự khởi tạo ca mặc định thứ Hai–thứ Sáu nếu công ty chưa có lịch, không ngày kết thúc;
  lịch riêng ưu tiên theo ngày hiệu lực. HR chọn ca có giờ bắt đầu/kết thúc cụ thể,
  các thứ áp dụng và khoảng ngày hiệu lực, xem trước rồi áp dụng phân công.
- `/attendance/shifts`: bật “Ca qua đêm” cho một khoảng giờ kết thúc ngày sau.
- `/attendance/overtime`: gửi/rút đăng ký OT, hộp duyệt HR, bắt đầu/kết thúc OT
  bằng thời gian server qua mạng công ty. Chỉ OT đã duyệt mới được chấm giờ.
- Bảng công và CSV tách số phút OT đã duyệt và thực tế khỏi công thường.
- Schema nghiệp vụ Attendance nằm trong `001_attendance_schema.sql`; khi bật thông báo cần thêm `004_notification_outbox.sql`. Database có dữ liệu cần kế hoạch nâng cấp riêng theo [database Attendance](../back-end/modules/attendance-service/README.md#database).

<a id="requests"></a>

## Đi trễ / về sớm — API đã kết nối

Route `/attendance/requests` dùng Attendance API thật, không còn ca mẫu, đơn mẫu
hoặc memory store. Lịch tham chiếu lấy `GET /api/v1/attendance/schedules/mine`
theo ngày chọn. HR/ADMIN mở **Hàng chờ duyệt** tại `/attendance/requests/inbox`; nhân viên xem,
sửa/rút đơn PENDING và lịch sử của mình. Danh sách phân trang/lọc trạng thái.

Prefix `/api/v1/attendance/requests`:

| Method / path       | Chức năng                                          |
| ------------------- | -------------------------------------------------- |
| GET /               | Danh sách bản thân, `status`, `page`, `size`       |
| GET /inbox          | Danh sách HR/ADMIN, cùng bộ lọc                    |
| POST /              | Tạo đơn, `Idempotency-Key` bắt buộc                |
| GET /{id}           | Chi tiết và ETag, chủ đơn hoặc HR/ADMIN            |
| PUT /{id}           | Chủ đơn sửa PENDING, `If-Match`                    |
| POST /{id}/cancel   | Chủ đơn rút PENDING, `If-Match`                    |
| POST /{id}/decision | HR/ADMIN duyệt/từ chối, `If-Match`, không tự duyệt |
| GET /{id}/history   | Lịch sử có phân trang, chủ đơn hoặc HR/ADMIN       |

Body gửi/sửa: `workDate`, `shiftId`, `shiftVersion`, `requestType`
(`LATE_ARRIVAL`/`EARLY_DEPARTURE`), `period` (`MORNING`/`AFTERNOON`),
`expectedTime` (`HH:mm`), `reason` (1–1.000 ký tự sau trim).
UI chỉ dùng `requestedMinutes` để xem trước; không gửi thời lượng, nhân viên hoặc
trạng thái tự quyết định. Server tính lại theo snapshot ca thật, trả `version`,
`requestedMinutes`, `roundedRequestedMinutes`, `definition` và thông tin xét duyệt.
Decision: `{"status":"APPROVED|REJECTED","reviewNote":"..."}`; từ chối bắt buộc lý do.

Ngày gửi/sửa từ hôm nay đến 365 ngày tới, giờ phải nằm trong buổi làm việc; nghỉ
cả buổi dùng Leave. Không nhận đơn trùng ngày/buổi/loại còn PENDING hoặc APPROVED,
không cho hai đơn trễ/sớm bao phủ hết buổi. Chặn ngày nghỉ chung hoặc buổi đã có
Leave PENDING/APPROVED. Ngày đã qua không sửa nhưng HR vẫn được xét duyệt đơn đã gửi.
Khi lịch đổi trong lúc chờ duyệt, yêu cầu tải lại/sửa trước khi duyệt. Phân công
không được thay lịch ảnh hưởng đơn đã duyệt. Đơn đã xử lý không sửa/rút trong bản này.

**Quy tắc đã thống nhất:** đơn được duyệt ghi nhận có phép, vẫn trừ công theo
bước làm tròn 15 phút. Không cộng thời gian làm, không trừ số dư phép năm.
Bảng công/CSV đối soát (`detail=true`) bổ sung `permissionCoverage`: ID đơn chờ/duyệt/xung đột, phút đã được
phép, giây thiếu thực tế nằm trong phạm vi được phép, số phút thiếu chưa có phép
làm tròn riêng để đối soát. Các trường có phép/chưa có phép không dùng để cộng lại
thay công chuẩn. Lý do/ghi chú riêng tư chỉ ở API đơn, không xuất CSV.

Ví dụ ca 13–15h, đơn xin tới 13:08 được duyệt, thực tế tới 13:16:
16 phút trễ → trừ 30 phút → 90 phút công; có phép 8 phút, còn 8 phút chưa có phép
→ đối soát 15 phút chưa có phép. Phê duyệt trong cùng Attendance DB hiển thị ở lần
đọc bảng công tiếp theo; coverage Leave/Calendar tự cập nhật ở kỳ mở; kỳ đóng giữ snapshot FINAL.

Khởi tạo database bằng [001_attendance_schema.sql](../back-end/modules/attendance-service/docs/sql/001_attendance_schema.sql)
trước khi chạy backend. File này đã gồm schema đơn đi trễ/về sớm. Nếu bật thông báo Kafka,
áp thêm `004_notification_outbox.sql` theo [Notification](../back-end/modules/notification-service/README.md).
UI giữ key khi retry gửi đơn mất phản hồi; 412 yêu cầu tải lại dữ liệu và sửa lại.

Attendance hiện ghi một cặp vào/ra mỗi ngày. Xin theo buổi không tự tạo sự kiện
rời/quay lại trong ngày; chỉ thời gian thiếu đo được từ punch mới được đối soát.

## Xem số ngày phép và chốt tháng

Form gửi phép có **Tính số ngày nghỉ**, gọi API estimate theo ngày/buổi; đổi ngày
xóa kết quả cũ. Số ngày theo lịch ca, bỏ ngày không có ca/ngày lễ, kiểm tra lại khi gửi/duyệt.

Bảng công có phần **Chốt công theo tháng** áp dụng toàn công ty, độc lập bộ lọc báo cáo.
HR/ADMIN xem trạng thái, nhập lý do rồi chốt/mở lại. Hiển thị lịch sử và nút kiểm tra
phép/ngày nghỉ thay đổi sau chốt. Kỳ FINAL ẩn nút refresh từng ngày; tải báo cáo/CSV
dùng dữ liệu đã chốt. Chưa có payroll hoặc tự sửa kỳ đã chốt bằng sự kiện.

## Tiếng Việt / English

Giao diện hỗ trợ Việt/Anh, đổi tại menu tài khoản hoặc trang đăng nhập/kích hoạt.
Lựa chọn được lưu và đồng bộ giữa các tab, không cần tải lại trang.
Sửa nội dung trong `src/locales/vi.json` và `src/locales/en.json`; xem
[hướng dẫn i18n](src/content/README.md) về khóa dịch, tham số và định dạng ngày/số.

### Quản trị tài khoản

`src/accounts/` chứa API, enum và xử lý lỗi; nội dung màn hình nằm trong `accountManagement`
của `locales/vi.json` và `locales/en.json`. Chỉ ADMIN truy cập `/accounts` và `/accounts/:id`.
Tài khoản chờ kích hoạt bị khóa sẽ không dùng được link cũ; mở khóa vẫn phải gửi lại lời mời
và hoàn tất kích hoạt. Các API quản trị dùng Bearer, không dùng refresh cookie để cấp quyền.
Thay đổi có hiệu lực với request xác thực tiếp theo; request đang xử lý có thể vẫn hoàn tất.
