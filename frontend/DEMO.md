# Demo frontend không cần backend

Bản demo dùng chính Vue frontend hiện tại, với adapter API và dữ liệu giả trong
`src/demo/`. Không cần Java, Gateway, database hoặc tài khoản thật. Không cần cài
thêm dependency.

## Chạy ngay

```sh
cd frontend
npm ci # chỉ cần nếu chưa cài dependency; Node.js 24
npm run dev:demo
```

Mở **http://localhost:5175**. Chọn **Quản trị viên**, **Nhân sự** hoặc **Nhân viên**
trên thanh HRM DEMO để vào ngay. Có thể đổi vai trò trong suốt buổi trình bày.
Cổng demo riêng 5175 tránh cổng frontend thường 5173; nếu đã bận, thêm `-- --port 5176`.

Đăng nhập bằng form cũng được:

| Vai trò  | Email                 | Mật khẩu demo |
| -------- | --------------------- | ------------- |
| ADMIN    | admin@demo.example    | Demo@123456   |
| HR       | hr@demo.example       | Demo@123456   |
| EMPLOYEE | employee@demo.example | Demo@123456   |

## Có gì trong demo

- 24 nhân viên, 6 phòng ban, 7 chức danh, hồ sơ và tài khoản mẫu.
- Tạo/sửa nhân viên, phòng ban, chức danh; đổi trạng thái nhân viên.
- Nghỉ phép: tạo, rút, duyệt/từ chối, lịch sử, số dư, lịch cá nhân và điều chỉnh
  đơn đã duyệt. Đổi sang HR để duyệt đơn do nhân viên gửi.
- Chấm công: giờ vào/ra, dữ liệu công theo khoảng ngày, đơn đi trễ/về sớm,
  bổ sung công, OT, mẫu ca, phân công và xuất CSV.
- Lịch công ty: tạo/sửa, xuất bản, hủy, xóa bản nháp.
- Tài khoản: tạo, khóa/mở khóa, đổi vai trò, lịch sử thao tác và mô phỏng cấp tài khoản.
- Dữ liệu và phiên demo lưu riêng trong localStorage; tải lại trang vẫn giữ thay đổi.
  Bấm **Khôi phục dữ liệu mẫu → Khôi phục** để bắt đầu lại.
- Giao diện Việt/Anh, phân quyền điều hướng và các trang trợ giúp hiện có.

Ngày mẫu được tạo theo thời điểm khởi tạo/reset; dữ liệu công được sinh theo
khoảng ngày đang xem. Mỗi trình duyệt có bộ dữ liệu riêng, không đồng bộ giữa máy.

## Build và triển khai website tĩnh

```sh
npm run build:demo
npm run preview:demo
```

Bản build nằm ở **`frontend/dist-demo/`**, xem trước tại **http://localhost:4175**.
Đưa toàn bộ nội dung thư mục này lên static hosting. Nếu hosting tự build:

- Root directory: `frontend`
- Build command: `npm ci && npm run build:demo`
- Publish directory: `dist-demo`
- Node.js: 24

Demo dùng hash router (`/#/employees`), nên tải lại đường dẫn con không cần SPA
rewrite; asset dùng đường dẫn tương đối để phục vụ dưới thư mục con. Cần phục vụ
qua HTTP/HTTPS, không mở trực tiếp `index.html` bằng `file://`. Dùng HTTPS khi
đưa khách hàng truy cập. Chưa có hosting nào được tạo hoặc phát hành tự động.

`npm run dev` và `npm run build` vẫn dùng backend thật. Adapter và thanh chọn vai
trò chỉ được tải khi Vite chạy với `--mode demo`; build thông thường loại bỏ chúng.

## Phạm vi mô phỏng

Đây là bản trình diễn tương tác, không dùng để vận hành nhân sự thực tế. Dữ liệu
mẫu và tài khoản demo đều công khai trong trình duyệt. Không gửi email/kích hoạt
thật, không có Kafka, SSE server hay kiểm tra tình trạng backend thật; trang kết
nối dịch vụ hiển thị trạng thái giả lập. Các phép tính công/phép, xung đột phân
ca và khóa kỳ được đơn giản hóa; không thay thế quy tắc của backend. Không có
đăng nhập bảo mật hoặc thu hồi phiên trên máy khác. Tài khoản tạo mới trong demo
sử dụng mật khẩu demo chung; mật khẩu nhập trong form không được lưu.

## Kiểm tra

```sh
npm test
npm run build:demo
npm run test:demo
npm run build
```

`test:demo` tự build lại rồi chạy Chromium trên **bản build tĩnh**, kiểm tra các màn hình không gửi
request `/api`, chuyển vai trò, lưu dữ liệu sau reload, khôi phục và màn hình nhỏ.
Kết quả và trace nằm riêng trong `test-results-demo/`, không ghi đè bộ E2E thường.
Unit tests kiểm tra phân quyền, version, duyệt nghỉ phép/số dư/điều chỉnh, lịch,
CSV và phân công. Playwright cần browser đã cài (`npx playwright install chromium`).
