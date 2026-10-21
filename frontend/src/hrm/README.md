# HRM API

- `endpoints.js`: đường dẫn của API Employee, Department, Position và
  lời mời kích hoạt. Tên resource mà UI dùng được ánh xạ sang URL tại đây; đổi URL
  backend không cần đổi tên resource trong component.
- `api.js`: các thao tác nghiệp vụ, HTTP method, payload và header đặc thù.
- `../api/client.js`: chuyển object body thành JSON và tạo query string, sau đó
  gọi `auth.request` để dùng chung xử lý token, refresh và lỗi HTTP hiện có.
- `constants.js`, `utils.js`: nhãn hiển thị, định dạng tên và kiểm tra UUID.

Các hàm `hrm.*` giữ nguyên tham số và dữ liệu trả về. `api.js` tiếp tục export nhãn
và tiện ích để tương thích với các import hiện có; component mới có thể import
trực tiếp từ `constants.js` hoặc `utils.js`.

Khi đổi endpoint, sửa `endpoints.js`. Khi đổi method hoặc payload, sửa thao tác
tương ứng trong `api.js`. Khi thêm resource, khai báo trong `resources` trước khi
dùng `hrm.list/get/save`. Resource không được khai báo sẽ bị từ chối trước khi gửi.

`requestJson` nhận `body` là dữ liệu JSON chưa serialize, `query` là object phẳng.
Query bỏ qua `null`, `undefined`, chuỗi rỗng và giữ lại `0`, `false`. Các tùy chọn
như `headers`, `signal`, `responseType` được chuyển tiếp đến `auth.request`.
Client này dùng chung cho HRM, Attendance, Leave, Calendar và Notifications.
Prefix toàn hệ thống nằm trong `../api/config.js`.

```js
await hrm.list('employees', 0, 20)
await hrm.save('departments', departmentId, { code: 'HR', name: 'Nhân sự' })
await hrm.provision(employeeId, email, idempotencyKey)
```
