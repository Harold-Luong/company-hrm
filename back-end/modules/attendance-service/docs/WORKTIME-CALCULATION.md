# Helper tính công trong Attendance

`WorkTimeSupport.calculate(input)` là hàm hỗ trợ nội bộ của `attendance-service`,
thuộc package `com.company.attendance.service.worktime`. Helper nhận tham số rõ
ràng, không truy cập database/API hay đồng hồ hệ thống. Không có Maven module,
JAR phát hành hoặc dependency riêng; code và test được build cùng Attendance.

API/UI hiện tại vẫn dùng `AttendanceCalculator`; việc đưa các trường công hưởng
lương từ helper vào báo cáo API/UI chưa được triển khai. Việc chuyển code vào
service không thay đổi công thức hay contract của các endpoint hiện hành.

## Build và sử dụng

Từ thư mục `back-end/modules/attendance-service`:

```bash
./mvnw test
# Hoặc chỉ kiểm thử helper
./mvnw -Dtest=WorkTimeSupportTest test
```

Ví dụ hoàn chỉnh: [PaidLeaveExample.java](../examples/PaidLeaveExample.java).
Sau khi compile service, chạy ví dụ với các class nội bộ:

```bash
./mvnw -DskipTests compile
java --class-path target/classes examples/PaidLeaveExample.java
```

Kết quả nghỉ phép năm cả ngày trên ca 8 giờ:

```text
status=ON_LEAVE
workedMinutes=0
annualLeaveMinutes=480
payableMinutes=480
payableDays=1.000000
```

## API

```java
WorkTimeResult day = WorkTimeSupport.calculate(input);
WorkTimePeriodResult period = WorkTimeSupport.calculatePeriod(List.of(inputDay1, inputDay2));
```

| Tham số `WorkTimeInput` | Ý nghĩa |
| --- | --- |
| `workDate`, `zone` | Ngày bắt đầu ca và múi giờ nghiệp vụ |
| `schedule` | Các khoảng làm việc theo thứ tự, không trùng, tên buổi duy nhất; danh sách rỗng là không có lịch làm |
| `calendarDay` | `WORKDAY` hoặc `PAID_HOLIDAY` đã được xác minh là ngày nghỉ hưởng lương áp dụng cho nhân viên |
| `leaves` | Các đơn phép thuộc nhân viên/ngày này; `FULL_DAY` hoặc tên buổi khớp ca |
| `attendance` | Check-in/out thực tế dạng `Instant`; `Punch.none()` khi chưa có ghi nhận |
| `overtime` | Đơn OT, trạng thái duyệt và cặp giờ vào/ra OT riêng |
| `recordingClosesAt` | Thời điểm hết cửa sổ chấm công, dùng phân biệt đang làm và thiếu giờ ra |
| `now` | Mốc thời gian tính báo cáo; truyền rõ để cùng input luôn cho cùng kết quả |
| `policy` | Chuẩn phút/ngày, bước làm tròn trễ/sớm và mẫu số quy đổi công |

`WorkTimePolicy.standard()` dùng chuẩn 480 phút/ngày, trễ và sớm làm tròn riêng lên
15 phút, quy đổi theo `STANDARD_DAY`. Có thể dùng `ASSIGNED_SHIFT` nếu chính sách
coi làm đủ một ca part-time là một công. Ví dụ ca 4 giờ đầy đủ tương ứng 0,5 công
với `STANDARD_DAY`, hoặc 1 công với `ASSIGNED_SHIFT`. Đây là tham số nghiệp vụ,
không phải quy định pháp luật hay công thức tiền lương bắt buộc.

Giờ ca/OT dự kiến phải chính xác đến phút. Giờ thực tế giữ số giây (bỏ phần lẻ
nhỏ hơn một giây khi tính thời lượng). Ca qua đêm dùng `Instant` của ngày hôm sau
cho giờ kết thúc; ca dài tối đa 24 giờ. OT thuộc ngày bắt đầu OT.

## Kết quả và quy tắc

- `scheduledMinutes`: toàn bộ thời lượng ca, giữ nguyên cả vào ngày nghỉ Calendar.
- `requiredWorkMinutes`: phần ca còn phải làm sau khi áp dụng Calendar và phép APPROVED.
- `workedActualSeconds`: thời gian thực tế giao với các khoảng phải làm; loại giờ nghỉ giữa ca.
- `workedCountedMinutes`: phút phải làm trừ trễ và sớm đã làm tròn riêng, tối thiểu 0.
  Vào sớm/ở lại muộn không bù đi trễ và không tự tạo OT.
- `paidHolidayMinutes`, `annualLeaveMinutes`, `otherPaidLeaveMinutes`, `unpaidLeaveMinutes`:
  tách từng loại nghỉ. Phép PENDING/REJECTED/CANCELLED không giảm giờ phải làm.
- `payableMinutes = workedCountedMinutes + paidHolidayMinutes + annualLeaveMinutes + otherPaidLeaveMinutes`.
  Nghỉ không lương không được cộng.
- `workedDays`, `paidAbsenceDays`, `payableDays`: công quy đổi theo policy, 6 chữ số thập phân.
  Đây là công hưởng lương, **không phải số ngày trừ quỹ phép**. Leave Service vẫn sở hữu
  quỹ phép và chính sách nửa ngày/cả ngày; helper không ghi sổ số dư.
- Ngày nghỉ Calendar ưu tiên hơn phép; không áp dụng/trừ phút phép trên ngày đó.
  Ngày không có lịch làm không tự phát sinh công hay phút phép dù có Calendar/đơn phép.
  Không thể suy ra ngày nghỉ chỉ từ thứ Bảy/Chủ nhật: caller phải truyền lịch thực tế.
- Thiếu check-in/out, giờ đảo ngược hoặc dữ liệu phép xung đột: số công chưa xác định là
  `null`, không thay bằng 0. Phép có lương đã biết vẫn hiển thị riêng ở các trường phút nghỉ.
- Chỉ OT APPROVED mới tính; số phút OT là phần giao giờ thực tế/giờ đã duyệt, làm tròn
  xuống phút. OT không cộng vào công thường và không tính hệ số lương. OT thiếu giờ
  ghi nhận trả `null`, OT chưa duyệt trả 0. Có thể có OT riêng vào ngày nghỉ hưởng lương.
- `issues` và trạng thái chỉ rõ xung đột; `complete()` yêu cầu cả công thường và OT
  có kết quả. `status` ở cấp ngày mô tả công thường; mỗi OT có trạng thái riêng.

Tổng hợp kỳ chỉ nhận dữ liệu của **một nhân viên**, cùng policy/múi giờ, mỗi ngày một
lần. `knownPayableMinutes` là tổng phần đã xác định; `payableMinutes/payableDays`
trả `null` nếu còn ngày công chưa rõ. `incompleteDates` liệt kê ngày thiếu công/OT.
Với `STANDARD_DAY`, tổng công kỳ được chia từ tổng phút một lần; với `ASSIGNED_SHIFT`
cộng số công từng ca đã quy đổi. Kỳ rỗng trả 0. Lịch/OT chồng giờ giữa hai ngày,
ngày lặp hoặc mã OT lặp bị từ chối để tránh cộng trùng.

## Trách nhiệm bên gọi

Caller xác định nhân viên đủ điều kiện, lịch riêng/lịch mặc định có hiệu lực,
Calendar đã công bố và áp dụng, phép thuộc đúng ngày, cũng như quyền/ngày giờ duyệt
OT. Cung cấp snapshot nếu cần tái lập báo cáo. Helper không xác thực quyền, mạng
chấm công, không duyệt đơn hoặc thay dữ liệu nguồn. Khi tính một ngày riêng lẻ,
caller kiểm tra cả lịch/OT ngày liền kề; `calculatePeriod` tự kiểm tra chồng giờ
qua ngày trong tập input được truyền.

Đơn Late/Early có phép không tự bù công theo chính sách hiện tại nên không thay đổi
kết quả phút công của helper. Phân loại sai lệch có phép/chưa có phép vẫn do
Attendance đối soát. Việc sửa Leave để tính số ngày trừ quỹ theo Calendar/lịch,
nối kết quả vào UI/CSV và khóa kỳ là các bước tích hợp riêng.
