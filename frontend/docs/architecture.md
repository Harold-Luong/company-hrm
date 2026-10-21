# Cấu trúc frontend

Frontend dùng Vue Composition API và tổ chức theo nghiệp vụ. Component gọi API
nghiệp vụ, không ghép URL hoặc tự xử lý token. Tiện ích độc lập nghiệp vụ nằm trong
`shared/`; trạng thái có vòng đời Vue có thể tái sử dụng nằm trong `composables/`.

Nội dung hiển thị (kể cả text theo trạng thái và câu có tham số) nằm trong
`src/locales/vi.json` và `src/locales/en.json`; adapter ở `src/content/`.
Giới hạn nhập liệu, phân trang, timeout và chu kỳ cập nhật nằm trong `src/config/`. Xem [hướng dẫn sửa nội dung](../src/content/README.md)
để thay câu chữ mà không sửa template hoặc logic nghiệp vụ.

```text
src/
├── api/
│   ├── config.js           # Prefix API và encode path segment
│   ├── http.js             # Fetch, timeout, JSON/blob/stream, lỗi HTTP
│   ├── errors.js           # ApiError và thông báo lỗi chung
│   ├── client.js           # Request JSON có xác thực, query và serialize body
│   ├── headers.js          # If-Match và Idempotency-Key
│   └── pagination.js       # Dữ liệu trang rỗng
├── auth/                   # Phiên, refresh, login/logout, kích hoạt, vai trò
├── hrm/                    # API, endpoint, nhãn và tiện ích nhân sự
├── attendance/             # API, endpoint, ca, đơn, báo cáo, lỗi nghiệp vụ
├── leave/                  # API, endpoint, nhãn, kiểm tra đơn và lỗi nghiệp vụ
├── calendar/               # API, endpoint, lịch, biểu mẫu, ngày sự kiện
├── notifications/          # API stream, subscription, theo dõi cấp tài khoản
├── services/               # Danh mục và API kiểm tra dịch vụ
├── locales/                # Bản dịch vi/en, cùng bộ khóa và tham số
├── i18n/                   # Vue I18n, lưu locale, adapter nội dung reactive
├── content/                # Adapter bản dịch theo nghiệp vụ
├── config/                 # Giới hạn dữ liệu, polling, múi giờ và phân trang
├── shared/                 # Ngày/giờ theo locale, định dạng thời lượng
├── composables/            # Tải dữ liệu và vòng đời idempotency của form
├── components/             # Giao diện tái sử dụng, gồm AttendancePeriodPanel
├── views/                  # Ghép giao diện và điều phối nghiệp vụ của trang
├── layouts/                # Khung ứng dụng và điều hướng
└── router/
    ├── routes.js           # Khai báo route, lazy import, title, quyền
    └── index.js            # Khởi tạo router và guard phiên/quyền
```

## Luồng gọi API

`view/component → domain/api.js → api/client.js → auth.request → api/http.js`

`auth/session.js` dùng HTTP bên dưới trực tiếp cho login/refresh/logout. Không
import client có xác thực vào session để tránh vòng phụ thuộc. API kích hoạt công
khai cũng không khởi tạo hoặc refresh phiên. Các export cũ của `auth/api.js` vẫn
trỏ đến cùng `ApiError` và HTTP client để giữ tương thích.

Login/refresh/logout dùng cookie HttpOnly và CSRF header, có khóa giữa các tab.
`auth/cookies.js` lấy token CSRF qua endpoint `/csrf`; `session.js` chỉ chia sẻ mã
phiên không chứa credential trong storage. Refresh không tự retry khi lỗi mạng.
Xem README về nâng cấp phiên cũ, HTTPS và đăng xuất offline.

- Đổi prefix toàn bộ API ở `api/config.js`.
- Đổi URL của nghiệp vụ ở `endpoints.js` của module đó.
- Đổi method, payload hoặc header đặc thù ở `api.js` của module.
- `requestJson` nhận body chưa serialize và query là object phẳng; bỏ qua giá trị
  `null`, `undefined`, chuỗi rỗng, giữ `0` và `false`.
- Dynamic ID/action luôn là một path segment được encode. Không truyền cả đường
  dẫn vào một segment của `apiPath`.
- `versionHeader` giữ nguyên phiên bản đã đọc. Không tự lấy phiên bản mới rồi gửi
  lại thao tác ghi khi nhận 412/428.
- Giữ `notifyRequestChange` sau thành công của thao tác thay đổi đơn để sidebar
  và lịch cập nhật. Lỗi ghi không tự retry ở lớp JSON client.
- CSV và SSE đi cùng luồng xác thực bằng `responseType`; stream giữ `signal` và
  `Accept: text/event-stream`.

ESLint chặn import transport, config URL và endpoint trực tiếp trong views,
components, layouts. Giao diện vẫn có thể dùng session để kiểm tra người dùng/
quyền; API nghiệp vụ chịu trách nhiệm gửi request.

## Logic dùng chung

`useAsyncResource(loader, options)` quản lý `data`, `loading`, `error`, `load` cho
thao tác **đọc**. Chỉ response của lần tải mới nhất được cập nhật. Thay đổi source
sẽ tải lại ngay; scope bị hủy khiến response đến muộn bị bỏ qua. `enabled` dùng
để ngừng tải và xóa dữ liệu khi không còn chủ thể, ví dụ hồ sơ nhân viên.

```js
const { data, loading, error, load } = useAsyncResource(
  () => hrm.get('employees', employeeId.value),
  { source: employeeId, enabled: () => !!employeeId.value },
)
```

`initialValue` là factory để mỗi instance có dữ liệu riêng. `resetOnLoad: false`
chỉ dùng khi muốn giữ dữ liệu cũ trong khi tải lại. Composable bỏ qua phản hồi cũ,
không hủy request mạng. Không dùng nó cho thao tác ghi: cần biết thao tác có thể
đã được server thực hiện và giữ nguyên cơ chế xử lý lỗi nghiệp vụ.

Các trang có cả tải và ghi giữ `mutating` riêng; `busy` được tính từ `loading ||
mutating`. Hoàn tất tải danh sách không được tự mở khóa form đang gửi.

`useIdempotencyKey(form)` giữ cùng key khi thử lại nội dung chưa thay đổi; thay
đổi form reset key đồng bộ; gọi `reset()` khi một lần gửi đã hoàn tất. Key này
chỉ nằm trong bộ nhớ của form. Cấp tài khoản và chấm công có quy tắc lưu qua
reload riêng, tiếp tục dùng cơ chế lưu hiện có.

Ngày/giờ dùng `shared/date.js`, theo `Asia/Ho_Chi_Minh`. Không dùng ngày máy người
dùng để xác định ngày nghiệp vụ. Các quy tắc riêng như ca qua đêm, sự kiện có
thời điểm kết thúc không bao gồm vẫn nằm trong module tương ứng.

`attendance/helpers.js`, `leave/helpers.js`, `auth/format.js`, các export nhãn
của `hrm/api.js` giữ tương thích với import cũ. Code ứng dụng đã chuyển sang import
module sở hữu dữ liệu: `constants.js`, `errors.js`, `shifts.js`, `shared/`.

## Phạm vi rà soát và lựa chọn refactor

| Khu vực                         | Thay đổi                                                               |
| ------------------------------- | ---------------------------------------------------------------------- |
| API các nghiệp vụ               | Chung query/JSON/header, tập trung prefix, tách endpoint               |
| Auth                            | Tách transport/lỗi khỏi session; giữ rotation, khóa nhiều tab và guard |
| Hồ sơ cá nhân                   | Chung cơ chế tải, reset dữ liệu, bỏ qua phản hồi của chủ thể cũ        |
| Danh sách đơn/ca                | Chung cơ chế tải mới nhất; tách trạng thái tải và ghi                  |
| Form đơn đi trễ, điều chỉnh, OT | Chung vòng đời idempotency key                                         |
| Báo cáo chấm công               | Tách khối chốt/mở kỳ thành component có props/event rõ ràng            |
| Router                          | Tách dữ liệu route khỏi guard, giữ lazy loading và quyền               |
| Nhãn, ngày giờ, lỗi             | Bỏ phụ thuộc tiện ích chéo giữa Attendance/Leave/Auth                  |
| Component, CSS và bố cục        | Giữ các component/design system hiện có; chuẩn hóa format              |

Các luồng có quy tắc riêng như provisioning, SSE reconnect, sửa lịch có kiểm tra
version, điều chỉnh phép và đối soát công vẫn giữ logic nghiệp vụ riêng. Không ép
chúng vào một form hoặc CRUD component chung với nhiều cờ điều kiện.

## Kiểm tra khi sửa

```sh
npm run lint
npm run test
npm run build
npm run test:e2e
```

Unit tests bao gồm dữ liệu/lỗi đến sai thứ tự, đổi chủ thể, hủy scope, idempotency,
contract request, CSV/SSE và notification sau mutation. E2E mặc định dùng API giả
lập; test với backend thật cần cấu hình opt-in như hướng dẫn README.

## Ngôn ngữ giao diện

Câu chữ và nhãn trạng thái tập trung trong `locales/vi.json` và `locales/en.json`.
`content/*` đọc bản dịch qua getter để việc đổi ngôn ngữ cập nhật tại chỗ.
Route title là hàm; router cập nhật tiêu đề tab theo route và locale. Các bộ
lọc dùng ID/enum ổn định, không dùng nhãn dịch làm giá trị nghiệp vụ.
`useLocalizedMessage` giữ resolver cho thông báo để không lưu bản dịch cũ.
Xem [hướng dẫn nội dung](../src/content/README.md) để thêm khóa và tham số.
