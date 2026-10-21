# Nội dung giao diện và i18n

Câu chữ nằm trong [`../locales/vi.json`](../locales/vi.json) và
[`../locales/en.json`](../locales/en.json). Hai file có cùng khóa, chia nhóm theo
màn hình/nghiệp vụ. `content/*.js` là adapter đọc bản dịch theo ngôn ngữ hiện tại;
không giữ câu chữ trong component hoặc sao chép bản dịch vào biến cố định.

Ví dụ đổi chữ trên nút kích hoạt: sửa `activation.actions.submitting` trong cả
hai file JSON, giữ nguyên khóa và logic của nút.

```json
{
  "activation": {
    "actions": {
      "submitting": "Đang kích hoạt…",
      "submit": "Đặt mật khẩu và kích hoạt"
    }
  }
}
```

```vue
<script setup>
import { activation as copy } from '@/content/auth.js'
</script>

<template>
  <button>{{ busy ? copy.actions.submitting : copy.actions.submit }}</button>
</template>
```

Adapter dùng getter nên Vue cập nhật ngay khi đổi ngôn ngữ. Đọc nội dung trong
render, `computed` hoặc getter; tránh `const label = copy.title` hay trải object
nhãn bên ngoài `computed`, vì chúng chụp lại bản dịch lúc khởi tạo.

## Câu có dữ liệu động

Đặt cả câu trong JSON, dùng tham số có tên thay vì nối từng đoạn dịch:

```json
{ "created": "Đã tạo tài khoản {email} thành công." }
```

Khai báo hàm trong adapter tương ứng và truyền dữ liệu từ nơi gọi:

```js
export const createAccount = defineContent('createAccount', {
  methods: { created: true },
})
copy.created({ email: form.email.trim() })
```

`parameters` cấp giá trị cấu hình cho các nhãn đọc trực tiếp, ví dụ độ dài mật
khẩu; `methods` có thể dùng hàm ánh xạ khi cần kết hợp dữ liệu và cấu hình.
Giữ tên biến giống nhau giữa hai ngôn ngữ. Các ký tự đặc biệt theo cú pháp
Vue I18n phải dùng literal interpolation, ví dụ `name{'@'}company.com`.
Nội dung dùng nội suy văn bản của Vue; không dùng `v-html` để render bản dịch.

Thông báo đang hiển thị có thể dùng `useLocalizedMessage(() => copy.invalidToken)`
và gán resolver `error.value = () => errorMessage(cause)` để đổi theo ngôn ngữ.
Resolver nên đọc dữ liệu ổn định của lần thao tác, không đọc lại form đã bị reset.

## Tổ chức

- `content/auth.js`, `hrm.js`, `attendance.js`, `leave.js`, `calendar.js`: adapter
  theo nghiệp vụ; `common.js` dành cho cùng một ý nghĩa được dùng nhiều nơi.
- `content/workspace.js`, `services.js`, `notifications.js`, `errors.js`: điều
  hướng, tiêu đề, dịch vụ và thông báo.
- Các file `constants.js` giữ enum/quyền, nhãn enum đọc từ locale qua adapter.
- `help/content.js` giữ cấu trúc hướng dẫn, ID, route và quyền; nội dung dài nằm
  dưới `help` trong JSON. Bộ lọc lưu ID ổn định, không lưu nhãn đã dịch.
- `config/validation.js`: giới hạn kiểm tra dữ liệu; `runtime.js`: thời gian chờ
  và polling; `presentation.js`: múi giờ công ty, phân trang và giới hạn badge.
- `i18n/index.js`: ngôn ngữ hỗ trợ, lưu lựa chọn và đồng bộ giữa các tab.
- `shared/date.js`, `shared/intl.js`: định dạng theo ngôn ngữ; ngày nghiệp vụ và
  timezone công ty không thay đổi theo lựa chọn ngôn ngữ.

Tiếng Việt là mặc định và fallback. Người dùng đổi Việt/Anh ở menu tài khoản
hoặc trang đăng nhập/kích hoạt/lỗi kết nối. Lựa chọn lưu dưới key `locale`; ứng
dụng vẫn đổi ngôn ngữ khi trình duyệt chặn storage. Tiêu đề tab và thuộc tính
`html.lang` cập nhật theo lựa chọn. Dữ liệu do người dùng/API cung cấp (tên,
mô tả sự kiện, ghi chú…) giữ nguyên nội dung gốc.

Vite cập nhật file khi phát triển; production cần build/deploy lại. Chạy
`npm test`, `npm run lint`, `npm run build` và `npm run test:e2e` sau khi sửa.
Unit test kiểm tra đủ khóa, biến nội suy và cú pháp hai catalog; ESLint chặn
text cứng trong template và chuỗi tiếng Việt trong script của component.
