# Company HRM — Frontend

Vue 3 + JavaScript + Vite, Vue Router, ESLint và Prettier. Giao diện doanh nghiệp
bằng tiếng Việt, responsive cho desktop/mobile, kết nối module Auth của backend.

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

Vite chuyển `/api/*` tới `http://localhost:8080`, giữ nguyên path. Đổi backend nếu cần:

```sh
cp .env.example .env.local
```

Sửa `API_PROXY_TARGET` rồi khởi động lại dev server. `.env.local` không được commit.
Không đặt secret trong biến `VITE_*` vì chúng được đưa vào mã phía browser.

## Trang và quyền truy cập

| Trang             | Đường dẫn       | Quyền                     |
| ----------------- | --------------- | ------------------------- |
| Đăng nhập         | `/login`        | Chưa đăng nhập            |
| Tổng quan         | `/`             | Mọi tài khoản đã xác thực |
| Tài khoản của tôi | `/account`      | Mọi tài khoản đã xác thực |
| Tạo tài khoản     | `/accounts/new` | HR **hoặc** ADMIN         |
| Từ chối truy cập  | `/forbidden`    | Tài khoản đã xác thực     |

- Bảo vệ cả điều hướng từ menu và truy cập URL trực tiếp; chưa đăng nhập được đưa
  về login rồi quay lại trang hợp lệ sau khi xác thực.
- Lấy vai trò hiện tại từ `/me` khi khôi phục phiên và mỗi lần điều hướng trang được
  bảo vệ. Không suy quyền từ JWT hoặc dữ liệu vai trò lưu ở browser.
- Hỗ trợ tài khoản nhiều vai trò; không có role hierarchy tự động. ADMIN không được
  suy thành HR. Quyền phía frontend phục vụ UX; backend vẫn kiểm tra từng API.
- Không có đăng ký công khai. Form cấp tài khoản yêu cầu Employee UUID hiện có theo
  contract của Auth, chỉ gọi `/auth/register`, không tạo/sửa/tìm kiếm Employee.
- Chưa triển khai module Employee, chấm công, danh sách tài khoản, sửa vai trò,
  khóa tài khoản hay quên mật khẩu. UI không giả lập các API chưa có.

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

## Phiên đăng nhập

Access token và thông tin người dùng chỉ nằm trong bộ nhớ. Refresh token lưu trong
`sessionStorage` của tab để giữ phiên khi reload, và bị xóa khi đăng xuất. Không
lưu mật khẩu, access token hoặc roles vào localStorage/sessionStorage.

Backend hiện trả refresh token trong JSON, chưa hỗ trợ HttpOnly cookie. Vì vậy
refresh token trong sessionStorage vẫn có thể bị đọc nếu xảy ra XSS. Khi chuyển
sang cookie HttpOnly cần điều chỉnh cả backend và cơ chế CSRF. Tab được nhân bản
có thể sao chép sessionStorage; vì refresh token chỉ dùng một lần, tab còn lại có
thể phải đăng nhập lại sau khi tab đầu xoay vòng token.

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
- E2E mặc định giả lập đúng contract Auth, không ghi vào database. Playwright chạy
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
`index.html` cho route frontend. Cấu hình reverse proxy `/api/*` tới backend
riêng, không fallback API về `index.html`. Dùng HTTPS khi triển khai thực tế.
Vite dev proxy không thay thế reverse proxy production; `npm run preview` chỉ để
xem thử build local.

Tham khảo [Vue Router navigation guards](https://router.vuejs.org/guide/advanced/navigation-guards.html)
và [Vue state management](https://vuejs.org/guide/scaling-up/state-management.html).
