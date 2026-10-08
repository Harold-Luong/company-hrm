# Thiết kế chung: lịch làm việc, nghỉ phép và chấm công

## Trạng thái và phạm vi

Tài liệu gồm thiết kế tổng thể và lộ trình mở rộng. [Attendance Service](attendance-service/README.md)
đã triển khai Java 21/JPA, ca cố định, phân công theo hiệu lực, check-in/out qua mạng
công ty, tổng hợp phép/ngày nghỉ và CSV tạm tính; có UI tương ứng. Mẫu mặc định
08:00–12:00, 13:30–17:30 tự áp dụng thứ Hai–thứ Sáu khi service khởi động nếu
công ty chưa có lịch. Lịch riêng và lịch công ty HR đã cấu hình được giữ nguyên.
Attendance cũng quản lý đơn đi trễ/về sớm, ca cố định qua đêm và OT có duyệt.
Leave hỗ trợ bước 0,5 ngày và endpoint coverage tối thiểu phục vụ Attendance.
Chưa có lịch linh hoạt, điều chỉnh công thủ công, chốt kỳ
hoặc tự đồng bộ nguồn. Các mô hình/contract dự kiến bên dưới không đồng nghĩa đã
triển khai toàn bộ; API hiện hành nằm trong README service.

Attendance hiện dùng lớp cha JPA chung cho metadata audit, version và xét duyệt;
giữ các bảng nghiệp vụ/lịch sử riêng để bảo toàn FK và snapshot. Báo cáo và lịch
cá nhân đọc dữ liệu theo khoảng ngày, tránh truy vấn từng ngày. Xem
[kiến trúc hiện hành](attendance-service/README.md#kiến-trúc-code-và-dữ-liệu)
và [schema khởi tạo duy nhất](attendance-service/docs/sql/001_attendance_schema.sql).
Ngày công, quy tắc lịch và đơn đi trễ/về sớm tham chiếu phiên bản ca bất biến thay
vì sao chép JSON ca. Snapshot nguồn được thu gọn theo từng ngày; vẫn giữ bằng
chứng chấm công từng ngày và ràng buộc chống ghi trùng vào/ra. Trong giai đoạn
phát triển, cập nhật schema này và tạo lại database khi thay đổi cấu trúc.

Thiết kế dùng chung cho giờ cố định, ca part-time và lịch linh hoạt chỉ yêu cầu
đủ thời lượng trong ngày. Trước mắt triển khai giờ cố định; các chế độ sau dùng
cùng mô hình dữ liệu. Ví dụ chính sách của nhiều công ty không đồng nghĩa triển
khai multi-tenant: phạm vi hiện tại vẫn là một công ty, `Asia/Ho_Chi_Minh`.

## 1. Hai chế độ tính công, độc lập với full-time/part-time

| Chế độ | Điều kiện hoàn thành | Đi trễ/về sớm | Ví dụ |
| --- | --- | --- | --- |
| `FIXED_SHIFT` | Làm trong các khoảng giờ được phân công | Đánh giá theo đầu/cuối phần ca phải làm | Mặc định 08:00–12:00, 13:30–17:30; có thể cấu hình 13:00–15:00 |
| `FLEXIBLE_DURATION` | Đủ thời lượng yêu cầu trong ngày | Không áp dụng | Đủ 8 giờ/ngày; hoặc part-time đủ 2,5 giờ/ngày |

Full-time/part-time mô tả lịch và thời lượng được phân công, không phải thuật
toán tính công thứ ba. Ca cố định 2 giờ dùng cùng thuật toán với ca cố định 8 giờ.
Lịch linh hoạt không cần khai báo một giờ bắt đầu/kết thúc giả để tính đi trễ.

Ưu tiên hiện tại là **`FIXED_SHIFT`**, phù hợp với lịch có khung giờ xác định,
kể cả ca part-time. **`FLEXIBLE_DURATION` thuộc giai đoạn nâng cao**, mặc định
ẩn và chỉ xuất hiện sau khi ADMIN mở khóa ở cấp công ty. HR và nhân viên không
được tự thay đổi trạng thái mở khóa; backend phải kiểm tra trạng thái này khi
tạo/sửa ca, kể cả khi gọi API trực tiếp. Việc mở khóa chỉ cung cấp thêm lựa chọn,
không tự chuyển các ca cố định đã có sang chế độ linh hoạt.

Đây là chính sách cho giai đoạn sau: hiện chưa triển khai cách tính công linh
hoạt hoặc chức năng mở khóa. UI chỉ dùng ca cố định và `ShiftService` vẫn từ chối
`FLEXIBLE_DURATION`, kể cả yêu cầu gửi bởi ADMIN.

Chính sách mặc định của công ty tạo phân công cho nhân viên. Cho phép phân công
riêng theo nhân viên và ngày hiệu lực; mỗi ngày phải xác định được duy nhất một
chính sách áp dụng. Không suy ra chế độ từ lần chấm công hoặc tự đổi chế độ khi
nhân viên đi trễ. Không có lịch hoặc lịch chồng lấn thì báo cần xử lý.

### 1.1. HR/Admin tạo ca và chọn phạm vi áp dụng

HR và ADMIN được tạo, sửa phiên bản ca `FIXED_SHIFT` và phân công; backend
kiểm tra quyền cho từng thao tác. Nhân viên xem lịch được gán, không tự sửa ca.

Ca mặc định ban đầu:

| Phần lịch | Thời gian | Thời lượng làm việc |
| --- | --- | --- |
| `MORNING` | 08:00–12:00 | 240 phút |
| Nghỉ trưa, không tính công | 12:00–13:30 | 0 phút |
| `AFTERNOON` | 13:30–17:30 | 240 phút |
| Cả ngày | Hai khoảng làm việc trên | 480 phút / 8 giờ |

Hai khoảng sáng/chiều thuộc **một ca/ngày**, không phải hai phân công hoặc hai
phiên chấm công. Giờ mặc định là dữ liệu cấu hình; không hard-code trong thuật
toán. HR/Admin có thể sửa từng khoảng hoặc tạo mẫu ca khác chỉ có một khoảng.

Ví dụ giữ sáng 08:00–12:00 và đổi chiều thành 13:00–15:00 thì ngày chuẩn là
**6 giờ**, nghỉ giữa giờ 12:00–13:00. Nếu chọn một ca chỉ gồm 13:00–15:00 thì
ngày chuẩn là **2 giờ**. Form hiển thị tổng thời lượng và khoảng nghỉ sau chỉnh
sửa để phân biệt hai lựa chọn này; không tiếp tục yêu cầu đủ 8 giờ cho ca đã đổi.

Luồng quản trị: tạo/chọn mẫu ca → chỉnh các khoảng giờ → chọn phạm vi → chọn
ngày bắt đầu, ngày kết thúc tùy chọn và các ngày làm việc áp dụng → xem trước
lịch cũ/mới, tổng giờ, nhân viên bị ảnh hưởng và xung đột → lưu áp dụng.

| Phạm vi | Hành vi |
| --- | --- |
| Mặc định công ty | Nhân viên không có phân công riêng kế thừa; nhân viên mới cũng kế thừa theo ngày hiệu lực và lịch làm việc |
| Một/vài nhân viên | Chọn danh sách nhân viên, tạo phân công riêng; không sửa mẫu đang áp dụng cho người khác |
| Toàn bộ nhân viên | Áp dụng cho mọi nhân viên đủ điều kiện trong khoảng hiệu lực, đồng thời thay thế phân công riêng trùng phạm vi sau khi hiển thị trong bản xem trước; cập nhật mặc định cho người gia nhập sau |

Phân công riêng có ưu tiên cao hơn mặc định công ty. Hai phân công riêng cùng
nhân viên chồng ngày không được âm thầm chọn theo thời điểm tạo: thao tác thay
thế phải tách/kết thúc khoảng hiệu lực cũ và ghi lịch sử. Hết hạn phân công
riêng thì quay về mặc định có hiệu lực lúc đó. Áp dụng toàn bộ không bỏ sót
nhân viên đang có lịch riêng; nếu chỉ muốn đổi mặc định và giữ ngoại lệ thì
chọn rõ phạm vi "Mặc định công ty".

Mẫu ca đã được dùng phải tạo revision mới khi sửa. Bước lưu áp dụng cập nhật
phân công tương lai trong phạm vi đã chọn, giữ snapshot lịch cũ ngoài phạm vi.
Ngày đã có chấm công/đơn đã duyệt bị ảnh hưởng hoặc thuộc kỳ đã khóa phải qua
luồng đối soát/điều chỉnh tương ứng; không đổi giờ hoặc thời lượng phép ngầm.
Thao tác hàng loạt cần có batch ID, version và idempotency; trả kết quả rõ ràng,
không báo thành công toàn bộ khi còn nhân viên chưa áp dụng được.

Validation: mỗi khoảng có giờ bắt đầu nhỏ hơn giờ kết thúc, các khoảng không
chồng lấn và nằm trong cùng ngày ở bản đầu; thời lượng chuẩn là tổng khoảng
làm việc. Nhãn buổi phục vụ ánh xạ phép không bị suy ra lại từ mốc 12:00.
Đổi chiều sang 13:00–15:00 thì nghỉ `AFTERNOON` loại đúng 120 phút khỏi công;
số dư phép vẫn theo đơn vị 0,5 ngày theo chính sách hiện tại. Ca một khoảng
phải có ánh xạ nghỉ nửa ngày rõ ràng trước khi nhận loại đơn đó (xem mục 4).

## 2. Trách nhiệm service và dữ liệu

| Service | Sở hữu |
| --- | --- |
| Employee | Nhân viên, phòng ban, ngày bắt đầu/kết thúc làm việc |
| Calendar | Ngày lễ/ngày nghỉ chung có hiệu lực |
| Leave | Đơn nghỉ cả ngày/nửa ngày, số dư phép, phê duyệt và lịch sử nghỉ phép |
| Attendance | Mẫu ca, phân công, sự kiện vào/ra, đơn đi trễ/về sớm và lịch sử duyệt, OT, công tạm tính và CSV; điều chỉnh/chốt kỳ còn trong lộ trình |

Chưa cần Shift Service hoặc Report Service riêng. Không đọc chéo database.
Attendance lấy dữ liệu qua API nội bộ có xác thực hoặc bản sao từ sự kiện;
ghi event/session/daily và kết quả idempotency trong transaction database cục bộ.

Mô hình dự kiến trong Attendance:

| Thành phần | Trường/quy tắc chính |
| --- | --- |
| `attendance_policies` | `mode`, `timezone`, quy tắc nghỉ giữa giờ, quy tắc làm tròn, ngày hiệu lực, version |
| `work_shifts` | Mẫu có tên, revision, các khoảng làm việc gắn nhãn buổi và giờ nghỉ cho `FIXED_SHIFT` |
| `work_schedule_rules` | Phạm vi công ty/danh sách nhân viên, ngày bắt đầu/kết thúc, ngày làm việc áp dụng, shift revision, lịch sử thay thế |
| `work_schedule_assignments` | Nhân viên, ngày công, policy/shift snapshot, thời lượng chuẩn, cửa sổ ghi nhận |
| `attendance_events` / `attendance_sessions` | Giờ vào/ra gốc, nguồn, thời điểm server nhận, phiên hợp lệ |
| `attendance_corrections` | Bổ sung/điều chỉnh có người thực hiện, lý do, lịch sử |
| `attendance_daily` | Thời lượng thực tế, được tính công, phép, sai lệch, nguồn và phiên bản tính |
| `attendance_periods` | Kỳ, trạng thái mở/khóa, revision báo cáo |

`FIXED_SHIFT` lấy thời lượng chuẩn từ tổng các khoảng làm việc của ca; không
lưu một mục tiêu độc lập có thể mâu thuẫn với ca. `FLEXIBLE_DURATION` có
`target_minutes` (ví dụ 480 hoặc 150) và cửa sổ được ghi nhận trong ngày.
Cửa sổ này xác định thời gian hợp lệ, không phải mốc đi trễ/về sớm.

Snapshot phân công phải gồm cả policy, giờ nghỉ, mục tiêu, lịch nghỉ và phiên
bản nguồn. Sửa mẫu ca/chính sách chỉ ảnh hưởng các phân công tương lai; tính lại
quá khứ là thao tác tường minh có audit. Thiếu dữ liệu nguồn không mặc định là
không có ngày lễ/đơn phép.

## 3. Giờ thực tế, nghỉ giữa giờ và ngày công

- Lưu timestamp gốc; không sửa giờ vào/ra thành giờ đã làm tròn.
- Gán `work_date` theo lịch và timezone. Bản đầu một phân công/ngày, một phiên
  vào/ra, không qua đêm; nhiều phiên/ca qua đêm cần mở rộng trước khi bật.
- Thời lượng được chứng minh bởi cặp vào/ra hợp lệ, trừ giờ nghỉ không tính công.
  Với nghỉ trưa cố định, chỉ trừ phần giao thực tế với khoảng nghỉ; không trừ
  một giờ nghỉ trưa cho ca 10:00–12:00 nếu ca không giao với giờ nghỉ.
- Lịch linh hoạt vẫn cần chính sách nghỉ rõ ràng. Ví dụ vào 09:00, ra 18:00,
  nghỉ 12:00–13:00 thì làm 8 giờ, không phải 9 giờ.
- Khi hỗ trợ nhiều phiên, lấy hợp các khoảng hợp lệ và loại giờ nghỉ một lần;
  không tính khoảng ra ngoài giữa hai phiên là giờ làm. Nghỉ linh hoạt cần
  dữ liệu break/phiên tương ứng, không suy ra từ lần vào đầu và ra cuối.
- Thiếu check-in/check-out: `INCOMPLETE`, chưa có thời lượng cuối cùng. Không
  có event sau hạn: `NO_RECORD`, cần đối chiếu phép trước khi kết luận vắng.
  Ngày nghỉ toàn bộ có đơn hợp lệ không đòi cặp vào/ra cho phần đã nghỉ.

## 4. Kết hợp nghỉ phép theo ngày

Leave giữ đơn vị **0,5 ngày** cho `ANNUAL`/`UNPAID`; chỉ `ANNUAL` trừ số dư phép
năm. Phút quy đổi phục vụ đối soát công, không thay đơn vị số dư và không tự
biến thiếu giờ thành đơn nghỉ phép.

- `FIXED_SHIFT`: nghỉ buổi sáng/chiều loại đúng khoảng tương ứng khỏi thời gian
  phải làm. Bản phân công cần ánh xạ buổi theo lịch, không hard-code nửa ngày
  bằng 4 giờ. Ví dụ nghỉ sáng thì xét đi trễ từ đầu ca chiều.
- `FLEXIBLE_DURATION`: đề xuất nghỉ 0,5 ngày giảm 50% mục tiêu ngày, nghỉ 1 ngày
  giảm toàn bộ mục tiêu. Mục tiêu 480 phút, nghỉ 0,5 ngày thì còn phải làm 240
  phút. Nếu không ràng buộc buổi nghỉ, UI/API tương lai cần lựa chọn `HALF_DAY`
  trung tính; giá trị này **chưa được API Leave hiện tại hỗ trợ**. Không âm thầm
  đổi nghĩa `MORNING`/`AFTERNOON` trong đơn cũ.
- Part-time: cần cấu hình quyền hưởng phép và ánh xạ nghỉ trước khi cho gửi đơn.
  Đề xuất nghỉ toàn bộ ngày được phân công là 1 ngày theo lịch cá nhân, nửa ngày
  là một nửa lịch/mục tiêu cá nhân; đây là chính sách cần chốt trước khi bật,
  không tự áp 12 ngày/năm hoặc coi ca 2 giờ là 0,5 ngày của lịch full-time.
- Ngày lễ/ngày không làm việc không trừ phép. Leave hiện còn tính theo ngày
  lịch nên phải nâng cấp cách tính và lưu snapshot trước khi tích hợp chính thức.
- Chỉ đơn `APPROVED` có hiệu lực; đơn chờ duyệt được hiển thị để HR đối soát.
  Khoảng nghỉ không được tính hai lần. Đi làm trùng khoảng phép cố định phải
  báo xung đột để xử lý; không tự hoàn số dư hoặc cộng hai lần vào công.

Ký hiệu dùng trong công thức: `B` là thời lượng chuẩn của ngày sau lịch nghỉ
chung, `L` là thời lượng nghỉ phép đã duyệt quy đổi, `R = max(0, B - L)` là phần
còn phải làm. Với fixed, `L` lấy từ phần giao với lịch; với flex lấy từ hạn mức
được duyệt theo chính sách. Luôn bảo đảm `0 <= L <= B`.

## 5. Thuật toán theo chế độ

### 5.1. `FIXED_SHIFT`

Xét giờ vào/ra so với phần ca phải làm sau khi loại phép và giờ nghỉ. Lưu riêng
đi trễ/về sớm thực tế và phần được quy đổi. Quy tắc đã chốt:

```text
round15(duration_seconds) = ceil(max(0, duration_seconds) / 900) * 15
```

0 phút → 0; 5 phút → 15; 15 phút → 15; 16 phút → 30; 31 phút → 45.
Không cắt bỏ giây trước khi tính: 15 phút 1 giây → 30 phút. Không có khoảng
miễn trễ mặc định; bước 15 phút không phải grace period.

Làm tròn riêng đi trễ và về sớm từng ca, rồi cộng ngày/tháng. Với một phiên
hợp lệ, phần phải làm chỉ thiếu ở đầu/cuối và chưa có chính sách bù công:

```text
work_minutes_counted = max(0, R - round15(late_seconds) - round15(early_seconds))
```

Phần thiếu đầu/cuối chỉ đếm phút phải làm, không tính giờ nghỉ/phép và không
đếm chồng nhau. Trừ vào `R`, không trừ lại từ giờ thực tế (tránh trừ hai lần).
Ca có dữ liệu không đầy đủ hoặc các khoảng vắng giữa giờ không được áp công
thức giản lược này. Khi mở rộng nhiều phiên, phải tính thêm các khoảng vắng
và chốt quy tắc quy đổi tương ứng trước khi đưa vào sử dụng.

Đi trễ rồi ở lại ngoài ca không tự bù. Lưu riêng thời gian ngoài lịch; không
tự công nhận overtime hoặc cộng vào công chuẩn.

### 5.2. `FLEXIBLE_DURATION`

`W` là thời lượng làm hợp lệ trong cửa sổ ngày, sau khi loại giờ nghỉ. Không
tính đi trễ/về sớm; các trường đó là `null`/không áp dụng, không gán 0 để giả
định nhân viên đã tuân thủ một ca cố định.

```text
shortfall_actual = max(0, R - W)
work_minutes_counted = min(R, W)
extra_minutes = max(0, W - R)
```

Đủ `R` thì hoàn thành yêu cầu, bất kể bắt đầu sớm hay muộn trong cửa sổ hợp lệ.
Thiếu thì ghi `SHORT_HOURS`. Không tự bù thiếu hôm nay bằng giờ dư hôm khác.
Phần dư được lưu riêng; chưa phải overtime đã duyệt.

**Đề xuất mặc định:** thiếu thời lượng linh hoạt giữ đúng số phút/giây thực tế,
`shortfall_rounding=NONE`. Quy tắc 15 phút đã chốt cho đi trễ/về sớm không tự
áp sang thiếu thời lượng. Nếu công ty chọn `CEIL_15`, làm tròn phần thiếu một
lần/ngày và tính `max(0, R - rounded_shortfall)`; không làm tròn giờ vào/ra hoặc
làm tròn riêng từng phiên. Lựa chọn này phải được lưu trong policy snapshot.

## 6. Đơn đi trễ/về sớm và bù công

Attendance lưu `attendance_requests` và `attendance_request_history`: nhân viên,
ngày công, snapshot ca, loại `LATE_ARRIVAL`/`EARLY_DEPARTURE`, buổi, giờ dự kiến,
lý do, trạng thái, version và lịch sử xét duyệt. API `/api/v1/attendance/requests`;
UI `/attendance/requests`. Giờ dự kiến chính xác đến phút và nằm trong khoảng
làm việc; backend tự tính số phút xin phép. Quy tắc làm tròn 15 phút áp dụng khi
tính sai lệch chấm công, không bắt buộc số phút xin phép là bội số của 15.

Chỉ áp dụng loại đơn này cho `FIXED_SHIFT`. Với flex thuần túy không có mốc trễ/
sớm, giao diện không yêu cầu đơn đó. Nếu tương lai có xin miễn một phần thời
lượng ngày, cần nghiệp vụ riêng; không dùng nhãn đi trễ cho thiếu giờ linh hoạt.

HR/ADMIN duyệt, cấm tự duyệt, dùng `If-Match`, audit và khóa điều phối trong
transaction Attendance để kiểm tra lịch và đơn khác. Attendance kiểm tra phép
qua REST của Leave; không có transaction chung giữa hai service. Phân công và
snapshot được xác minh trong Attendance, không tin giờ ca hoặc thời lượng client gửi.

Phê duyệt xác nhận khoảng vắng được cho phép, không tự đổi timestamp, trừ phép
năm hoặc bù đủ công. Đề xuất mặc định `permission_credit=NONE`; bù công có
lương cần chính sách riêng được cấu hình trước khi bật. Khi đối soát, lấy giao
của khoảng vắng thực tế với khoảng đã duyệt để xác định phần có phép. Giữ các
phần này ở độ chính xác thực tế; làm tròn tổng đi trễ/về sớm một lần theo mục 5,
không làm tròn từng phần có phép/chưa có phép rồi cộng gây tăng mức khấu trừ.

## 7. Ví dụ đối chiếu

Các ví dụ fixed không có bù công; flex dùng `shortfall_rounding=NONE`. Cặp giờ
trong bảng đủ dữ liệu; mọi giờ nghỉ đều ghi rõ.

| Lịch | Nghỉ phép | Ghi nhận | Giờ làm thực tế hợp lệ | Giờ làm tính công | Kết quả khác |
| --- | --- | --- | --- | --- | --- |
| Fixed mặc định 08–12, 13:30–17:30 | Nghỉ sáng | 13:38–17:30 | 3h52 | 3h45 | Phép 0,5 ngày; trễ thực tế 8p, quy đổi 15p |
| Fixed đã đổi 08–12, 13–15 | Nghỉ sáng | 13:08–15:00 | 1h52 | 1h45 | Ngày chuẩn 6h; phép sáng quy đổi 4h |
| Fixed chỉ có 13–15 | Không | 13:00–15:00 | 2h | 2h | Ngày chuẩn 2h, không thiếu 6h |
| Fixed 10–12 | Không | 10:08–12:00 | 1h52 | 1h45 | Trễ quy đổi 15p |
| Fixed 13–15:30 | Không | 13:08–15:30 | 2h22 | 2h15 | Trễ quy đổi 15p |
| Fixed mặc định 08–12, 13:30–17:30 | Không | 08:08–17:38, nghỉ 12–13:30 | 7h52 trong ca | 7h45 | Ngoài ca 8p, không bù trễ |
| Flex 8h, cửa sổ 06–22 | Không | 09:08–18:08, nghỉ 12–13 | 8h | 8h | Đủ thời lượng, không có đi trễ |
| Flex 8h, cửa sổ 06–22 | Không | 09:08–18:00, nghỉ 12–13 | 7h52 | 7h52 | Thiếu 8p; nếu policy chọn CEIL_15 thì công là 7h45 |
| Flex 8h, cửa sổ 06–22 | Nghỉ 0,5 ngày, quy đổi 4h | 13:08–17:08 | 4h | 4h | Đủ mục tiêu còn lại; phép ghi riêng |
| Flex part-time 2h30, cửa sổ 06–22 | Không | 14:00–16:30, không có giờ nghỉ | 2h30 | 2h30 | Đủ thời lượng |

## 8. Tổng hợp, chốt công và CSV

Kết quả chung cho cả hai chế độ, một dòng/nhân viên/ngày:

- Nhân viên, mã/phòng ban tại kỳ, ngày công, mode, assignment/policy version.
- `base_required_minutes`, `leave_days`, `annual_leave_minutes`,
  `unpaid_leave_minutes`, `remaining_required_minutes`.
- Giờ vào/ra, `worked_actual_minutes`, `work_minutes_counted`.
- Fixed: phút trễ/sớm thực tế, phút trễ/sớm quy đổi, khoảng vắng có phép và
  request IDs. Flex: `shortfall_actual_minutes`, `shortfall_counted_minutes`.
- Phút ngoài lịch/phần dư, trạng thái dữ liệu, đơn đang chờ, nguồn/phép đã áp
  dụng, calculation revision và trạng thái kỳ.

Không đồng nhất giờ làm tính công với công hưởng lương. Phép năm hưởng lương
được ghi riêng; nghỉ không lương không cộng vào công hưởng lương. Nếu công ty
cấu hình phép năm được cộng vào chỉ tiêu công hưởng lương thì cộng phần phép
đó với công làm việc, tối đa thời lượng chuẩn ngày cho phần công thông thường.
Không cộng phần dư thành overtime tự động. Nghỉ flex giảm mục tiêu nhưng làm
vượt mục tiêu còn lại phải hiện phần dư để đối soát, không cộng phép và giờ
dư hai lần, cũng không tự hoàn phép.

Màn hình và CSV đọc cùng revision tổng hợp. CSV chi tiết giữ cột mode và giá trị
không áp dụng để phân biệt thiếu giờ với đi trễ. Tổng tháng cộng từ ngày đã tính,
không làm tròn lại hoặc quy đổi mọi nhân viên sang ngày chuẩn 8h. Xuất UTF-8 BOM,
escape CSV và vô hiệu hóa ô có nguy cơ bị hiểu là công thức; không xuất lý do
riêng tư mặc định. Phân quyền xem/export thực thi ở backend.

Luồng đối soát: tính tạm → xử lý thiếu dữ liệu/đơn chờ → xác minh nguồn đã đồng
bộ → khóa kỳ → xuất bản chính thức. Báo cáo trước khóa ghi rõ tạm tính. Duyệt
đơn/correction đến muộn vào kỳ khóa phải báo chênh lệch, không ghi đè; mở lại
có quyền, lý do và audit rồi tạo revision mới. Khi Leave phát sự kiện, dùng
outbox cùng transaction duyệt và consumer chống trùng/sai thứ tự theo version.

## 9. Trình tự triển khai và kiểm chứng

1. Tạo Attendance với policy/phân công/snapshot; HR/Admin quản lý `FIXED_SHIFT`
   mặc định 08–12, 13:30–17:30 và ca tùy chỉnh, áp dụng toàn bộ hoặc nhân viên
   được chọn. Ghi nhận vào/ra và tổng hợp có kiểm soát dữ liệu thiếu.
2. Đơn đi trễ/về sớm đã triển khai trong Attendance, không thuộc Leave.
   Tiếp tục hoàn thiện tính phép theo lịch/ngày nghỉ và đối soát dữ liệu đã duyệt.
3. Đối soát, khóa kỳ, CSV chi tiết và tổng tháng.
4. Triển khai `FLEXIBLE_DURATION` như tính năng nâng cao mặc định ẩn, chỉ ADMIN
   được mở khóa, sau khi hoàn thiện tính công, UI/validation, chính sách nghỉ/nửa
   ngày flex và kiểm thử tương ứng. Chọn
   chế độ theo assignment, không tạo Attendance Service riêng cho từng chế độ.

Các ca nghiệm thu bắt buộc: toàn bộ ví dụ mục 7; 0/15/15 phút 1 giây ở biên
làm tròn; trễ 5p + sớm 5p → 30p; ca ngắn không cho công âm; nghỉ trưa giao một
phần; phép cả ngày không có event; thiếu check-out; ngày lễ không trừ phép;
flex làm dư không tự tính overtime; đơn trùng/duyệt đồng thời; event trùng/sai
thứ tự; đổi policy không đổi công đã khóa; CSV khớp revision trên màn hình.
Kiểm tra thêm quyền quản lý ca; lịch mặc định 480 phút; đổi riêng buổi chiều
thành 13–15 còn 360 phút/ngày; ca chỉ 13–15 là 120 phút; phân công vài nhân viên
không ảnh hưởng người khác; áp dụng toàn bộ xử lý cả ngoại lệ; hết hạn lịch riêng
quay về mặc định; ngày hiệu lực không làm đổi snapshot hoặc số dư phép quá khứ.

Đây là tiêu chí cho implementation tương lai, chưa phải bộ test đã chạy.
