// User-facing rules verified against the current UI and backend.
// Keep formulas aligned with AttendanceCalculator, AttendancePermissionService,
// OvertimeService, LeaveService and src/attendance/report.js when policies change.
export const helpGroups = [
  'Cá nhân & tài khoản',
  'Chấm công',
  'Nghỉ phép',
  'Nhân sự & lịch',
  'Hệ thống',
]
const personal = 'Mọi tài khoản đã đăng nhập'
const reviewers = 'HR / ADMIN'

export const features = [
  {
    id: 'overview',
    group: helpGroups[0],
    title: 'Tổng quan & chấm công nhanh',
    path: '/',
    access: personal,
    description:
      'Xem thông tin phiên đăng nhập, vai trò, liên kết nhanh và ghi nhận giờ vào/ra của ca hiện tại.',
    rules: [
      'Ngày, giờ chấm công theo giờ Việt Nam. Giờ vào/ra được hệ thống ghi tại thời điểm thao tác.',
      'Chấm công nhanh dùng cùng dữ liệu với trang Chấm công của tôi.',
    ],
  },
  {
    id: 'profile',
    group: helpGroups[0],
    title: 'Hồ sơ của tôi',
    path: '/account',
    access: personal,
    description:
      'Xem hồ sơ cá nhân, công việc, tổng hợp công theo tháng, số dư phép theo năm, lịch sử nghỉ phép và thông tin tài khoản.',
    rules: [
      'Công thực tế = tổng phút công đã xác định trong tháng; tăng ca = tổng phút OT ghi nhận.',
      'Ngày phép = tổng ngày phép năm và nghỉ không lương được áp dụng trong bảng công. Số này có thể khác số ngày của đơn nghỉ phép.',
      'Phép đã dùng bao gồm cả đơn nghỉ trong tương lai đã được duyệt.',
    ],
    formulaIds: ['work-time', 'leave-days', 'leave-balance'],
  },
  {
    id: 'session',
    group: helpGroups[0],
    title: 'Đăng nhập, kích hoạt & đăng xuất',
    path: '/account',
    access: personal,
    description:
      'Đăng nhập bằng tài khoản được cấp; kích hoạt qua liên kết email nếu được mời; đăng xuất phiên hiện tại hoặc tất cả thiết bị trong phần tài khoản.',
    rules: [
      'Mở email mời kích hoạt, dùng liên kết để thiết lập mật khẩu rồi đăng nhập.',
      'Đăng xuất tất cả thiết bị cần xác nhận trong Hồ sơ của tôi. Chưa có màn hình quên mật khẩu hoặc tự đăng ký công khai.',
    ],
  },
  {
    id: 'attendance',
    group: helpGroups[1],
    title: 'Chấm công của tôi',
    path: '/attendance',
    access: personal,
    description:
      'Xem ca làm việc, ghi giờ vào/ra, lọc kỳ công, xem trạng thái và lịch sử từng ngày.',
    rules: [
      'Cần mạng công ty, lịch làm việc có hiệu lực và hồ sơ nhân viên đủ điều kiện. Một cặp giờ vào/ra mỗi ngày công.',
      'Ngày đã hoàn tất là ngày có trạng thái Đã kết thúc. Ngày đi trễ/về sớm được đếm một lần nếu một trong hai số phút lớn hơn 0.',
      'Ca qua đêm thuộc ngày bắt đầu ca; cần kết thúc ca đêm đang mở trước khi vào ca mới.',
    ],
    formulaIds: ['work-time', 'actual-time', 'rounding', 'report-totals'],
  },
  {
    id: 'permissions',
    group: helpGroups[1],
    title: 'Xin đi trễ / về sớm',
    path: '/attendance/requests',
    access: personal,
    description:
      'Chọn ngày, ca, buổi, giờ dự kiến và lý do; gửi, sửa/rút đơn chờ duyệt, xem lịch sử xử lý.',
    rules: [
      'Đơn theo buổi phải nằm trong giờ làm việc. Nghỉ cả buổi dùng đơn nghỉ phép.',
      'Đơn được duyệt ghi nhận có phép, vẫn trừ công theo bước 15 phút và không trừ số dư phép năm.',
      'Không sửa đơn của ngày đã qua; đơn đã xử lý không sửa/rút ở màn hình này.',
    ],
    formulaIds: ['permission-time', 'rounding'],
  },
  {
    id: 'permission-review',
    group: helpGroups[1],
    title: 'Duyệt đi trễ / về sớm',
    path: '/attendance/requests/inbox',
    access: reviewers,
    restricted: true,
    description:
      'Lọc danh sách, xem ca và thời lượng xin phép, duyệt hoặc từ chối kèm ghi chú, xem lịch sử.',
    rules: [
      'Chỉ xử lý đơn chờ duyệt; không tự duyệt đơn của mình. Từ chối phải có lý do.',
      'Khi ca thay đổi, người gửi cần tải lại và sửa đơn theo lịch mới trước khi được duyệt.',
    ],
    formulaIds: ['permission-time'],
  },
  {
    id: 'corrections',
    group: helpGroups[1],
    title: 'Bổ sung / Điều chỉnh công',
    path: '/attendance/corrections',
    access: 'Cá nhân gửi đơn · HR / ADMIN xét duyệt',
    description:
      'Gửi giờ vào/ra đề nghị sau khi ca kết thúc; sửa/rút đơn chờ, xem lịch sử. HR/ADMIN mở Hàng chờ duyệt ngay tại trang này.',
    rules: [
      'Giờ đề nghị phải hợp lệ, nằm trong cửa sổ chấm công và không ở tương lai.',
      'Sau khi được duyệt, bảng công tính lại từ giờ đã điều chỉnh; giữ giờ gốc và lịch sử để đối soát. Không tự duyệt đơn của mình.',
    ],
    formulaIds: ['work-time'],
  },
  {
    id: 'overtime',
    group: helpGroups[1],
    title: 'Tăng ca (OT)',
    path: '/attendance/overtime',
    access: 'Cá nhân gửi/chấm giờ · HR / ADMIN xét duyệt',
    description:
      'Đăng ký/rút OT chờ duyệt; HR/ADMIN xét duyệt trong Hàng chờ duyệt; nhân viên bấm bắt đầu và kết thúc OT được duyệt.',
    rules: [
      'Đăng ký và duyệt trước giờ bắt đầu, thời lượng dưới 24 giờ. Không trùng giờ làm thường hoặc khoảng phép bị chặn.',
      'Chấm OT qua mạng công ty. OT được tách khỏi công thường, chưa tính tiền hoặc hệ số lương.',
    ],
    formulaIds: ['overtime-time'],
  },
  {
    id: 'shifts',
    group: helpGroups[1],
    title: 'Ca làm việc',
    path: '/attendance/shifts',
    access: reviewers,
    restricted: true,
    description:
      'Tạo, sửa, vô hiệu hóa mẫu ca; xem lịch sử phiên bản; cấu hình khoảng làm việc, cửa sổ chấm công và ca qua đêm.',
    rules: [
      'Phút chuẩn = tổng thời lượng các khoảng làm việc; không cộng khoảng nghỉ giữa ca.',
      'Ca thường có một hoặc hai khoảng làm việc. Ca qua đêm có một khoảng kết thúc ngày hôm sau.',
      'Sửa mẫu ca tạo phiên bản mới; ngày công đã ghi nhận giữ ca đã lưu.',
    ],
    formulaIds: ['work-time'],
  },
  {
    id: 'schedules',
    group: helpGroups[1],
    title: 'Phân công & lịch làm việc',
    path: '/attendance/schedules',
    access: reviewers,
    restricted: true,
    description:
      'Chọn phạm vi nhân viên, ca, thứ trong tuần và ngày hiệu lực; xem trước, áp dụng lịch mặc định/lịch riêng hoặc thay lịch toàn bộ nhân viên; xem lịch sử.',
    rules: [
      'Trong ngày có hiệu lực, lịch riêng của nhân viên được ưu tiên hơn lịch mặc định công ty.',
      'Xem trước để kiểm tra xung đột. Sửa phạm vi hoặc ca phải xem trước lại trước khi áp dụng.',
      'Ngày không có ca được ghi riêng, không tự coi là vắng mặt.',
    ],
  },
  {
    id: 'reports',
    group: helpGroups[1],
    title: 'Bảng công & xuất CSV',
    path: '/attendance/reports',
    access: reviewers,
    restricted: true,
    description:
      'Chọn kỳ, nhân viên/phòng ban; xem tổng hợp từng người, tìm tên/mã, lọc bất thường, mở từng ngày, cập nhật phép/lịch và xuất CSV.',
    rules: [
      'Bốn chỉ số đầu trang và CSV dùng toàn bộ kỳ đã tải. Tìm kiếm, phân trang và lọc danh sách bên dưới không đổi phạm vi này.',
      'Ngày đã ghi nhận giữ bản chụp phép/lịch. Khi nguồn thay đổi, HR cần bấm cập nhật phép/lịch ở ngày đó.',
      'Bảng công hiện là tạm tính, chưa có chốt kỳ hay bảng lương. CSV xuất chi tiết theo ngày.',
    ],
    formulaIds: ['work-time', 'report-totals'],
  },
  {
    id: 'leave',
    group: helpGroups[2],
    title: 'Nghỉ phép của tôi',
    path: '/leave',
    access: personal,
    description:
      'Gửi đơn phép năm/nghỉ không lương cả ngày hoặc nửa ngày; lọc trạng thái, xem chi tiết/lịch sử và rút đơn đang chờ.',
    rules: [
      'Ngày bắt đầu từ hôm nay; đơn nửa ngày chỉ trong một ngày. Đơn phép năm không kéo dài qua hai năm.',
      'Hiện số ngày trong đơn tính theo ngày lịch, bao gồm cuối tuần và ngày lễ; không tự loại theo phân ca.',
    ],
    formulaIds: ['leave-days', 'leave-balance'],
  },
  {
    id: 'leave-review',
    group: helpGroups[2],
    title: 'Duyệt nghỉ phép',
    path: '/leave/inbox',
    access: reviewers,
    restricted: true,
    description:
      'Xem đơn chờ, duyệt/từ chối có ghi chú, xem lịch sử và chuyển bộ lọc sang điều chỉnh phép đã duyệt.',
    rules: [
      'Không tự duyệt đơn của mình; từ chối phải có lý do. Phép năm chỉ trừ số dư khi duyệt thành công.',
      'Đơn nghỉ không lương, đơn chờ, bị từ chối hoặc rút không trừ phép năm.',
    ],
    formulaIds: ['leave-balance'],
  },
  {
    id: 'leave-adjustments',
    group: helpGroups[2],
    title: 'Điều chỉnh phép đã duyệt',
    path: '/leave',
    access: 'Chủ đơn gửi · HR / ADMIN xét duyệt',
    description:
      'Mở chi tiết đơn đã duyệt để yêu cầu hủy toàn bộ hoặc rút ngắn phần còn nghỉ; theo dõi, rút yêu cầu chờ và xem lịch sử.',
    rules: [
      'Đơn gốc và số dư chỉ thay đổi sau khi HR/ADMIN khác duyệt điều chỉnh.',
      'Phép năm hoàn lại phần chênh lệch. Ngày công đã ghi nhận cần cập nhật phép/lịch để nhận thay đổi.',
    ],
    formulaIds: ['leave-balance'],
  },
  {
    id: 'employees',
    group: helpGroups[3],
    title: 'Quản lý nhân viên',
    path: '/employees',
    access: reviewers,
    restricted: true,
    description:
      'Xem danh sách, tạo/sửa hồ sơ cá nhân, liên hệ, người thân, ngày vào làm, phòng ban, chức danh, người quản lý và trạng thái công việc.',
    rules: [
      'Trạng thái công việc và trạng thái tài khoản là hai thông tin riêng. Không có công thức tính công trong màn hình hồ sơ.',
    ],
  },
  {
    id: 'departments',
    group: helpGroups[3],
    title: 'Phòng ban',
    path: '/departments',
    access: reviewers,
    restricted: true,
    description:
      'Xem danh sách, tạo/sửa mã, tên và mô tả phòng ban để sử dụng trong hồ sơ và bộ lọc.',
    rules: ['Đây là danh mục, không có công thức tính số liệu.'],
  },
  {
    id: 'positions',
    group: helpGroups[3],
    title: 'Chức danh',
    path: '/positions',
    access: reviewers,
    restricted: true,
    description: 'Xem danh sách, tạo/sửa mã, tên và mô tả chức danh để gán cho nhân viên.',
    rules: ['Đây là danh mục, không có công thức tính số liệu.'],
  },
  {
    id: 'accounts',
    group: helpGroups[0],
    title: 'Tạo & cấp tài khoản nhân viên',
    path: '/accounts/new',
    access: 'ADMIN',
    roles: ['ADMIN'],
    restricted: true,
    description:
      'Tạo tài khoản cho hồ sơ nhân viên hiện có, chỉ định vai trò; hoặc cấp tài khoản từ hồ sơ nhân viên và theo dõi kết quả, email kích hoạt/gửi lại.',
    rules: [
      'Tài khoản phải gắn với hồ sơ nhân viên. Màn hình tạo tài khoản không tự tạo hồ sơ nhân viên.',
      'Chưa có giao diện danh sách tài khoản, thay đổi vai trò hay khóa tài khoản.',
    ],
  },
  {
    id: 'calendar',
    group: helpGroups[3],
    title: 'Lịch nghỉ & sự kiện',
    path: '/calendar',
    access: personal,
    description:
      'Xem lịch theo năm, lọc loại, xem ngày/sự kiện và nghỉ phép cá nhân đang chờ hoặc đã duyệt; mở chi tiết đơn nghỉ.',
    rules: [
      'Sự kiện cả ngày gồm cả ngày kết thúc. Sự kiện theo giờ không gồm thời điểm kết thúc; hiển thị theo giờ Việt Nam.',
      'Số kỳ nghỉ/sự kiện trong năm đếm các sự kiện chung và đơn nghỉ cá nhân giao với năm đang xem. Số sự kiện tháng đếm các sự kiện giao với tháng sau khi lọc loại; một sự kiện nhiều ngày chỉ đếm một lần.',
      'Chỉ ngày nghỉ chung đã công bố, cả ngày, áp dụng toàn công ty mới được dùng để miễn giờ làm trong bảng công.',
    ],
  },
  {
    id: 'calendar-management',
    group: helpGroups[3],
    title: 'Quản lý lịch',
    path: '/calendar-events',
    access: reviewers,
    restricted: true,
    description: 'Lọc danh sách sự kiện; tạo/sửa sự kiện, công bố, hủy có lý do hoặc xóa bản nháp.',
    rules: [
      'Bản nháp chưa công bố không tạo ngày nghỉ chung trong bảng công. Sự kiện đã hủy vẫn có thể hiển thị trên lịch với nhãn đã hủy.',
    ],
  },
  {
    id: 'notifications',
    group: helpGroups[4],
    title: 'Thông báo & hàng chờ',
    path: '/leave/inbox',
    access: reviewers,
    restricted: true,
    description:
      'Theo dõi huy hiệu số đơn chờ duyệt trên menu và thông báo kết quả cấp tài khoản; mở trang liên quan để xử lý.',
    rules: [
      'Huy hiệu nghỉ phép gồm đơn nghỉ đang chờ và yêu cầu điều chỉnh đang chờ. Huy hiệu đi trễ/về sớm đếm đơn chờ của nghiệp vụ này.',
      'Số trên huy hiệu là toàn bộ hàng chờ; số đơn chờ trong bảng công chỉ tính phạm vi kỳ báo cáo.',
    ],
  },
  {
    id: 'services',
    group: helpGroups[4],
    title: 'Kết nối dịch vụ',
    path: '/services',
    access: personal,
    description:
      'Kiểm tra trạng thái Tài khoản, Nhân viên, Lịch, Chấm công, Nghỉ phép và Thông báo; bấm Kiểm tra lại khi kết nối gián đoạn.',
    rules: ['Trạng thái phản ánh lần kiểm tra gần nhất. Không có công thức nghiệp vụ ở trang này.'],
  },
]

export const formulas = [
  {
    id: 'work-time',
    title: 'Giờ tính công đã xác định',
    expression:
      'Phút phải làm = Phút chuẩn theo ca − Phút phép năm − Phút nghỉ không lương\nPhút tính công = max(0, Phút phải làm − Phút trễ làm tròn − Phút về sớm làm tròn)',
    explanation:
      'Áp dụng khi đủ giờ vào/ra hợp lệ, không xung đột phép/lịch. Phút chuẩn là tổng các khoảng làm việc của ca, bỏ khoảng nghỉ. Chỉ phép đã duyệt khớp buổi/ngày mới được áp dụng. Nếu có điều chỉnh đã duyệt, dùng giờ vào/ra đã điều chỉnh.',
    example:
      'Ca 08:00–12:00 và 13:30–17:30: 480 phút. Vào 08:07, ra 17:20, không nghỉ phép → 480 − 15 − 15 = 450 phút = 7 giờ 30 phút.',
    notes: [
      'Tổng trên báo cáo = cộng phút tính công đã xác định của các nhân viên/ngày trong kỳ đã tải. Không mặc định một ngày bằng 8 giờ.',
      'Phép năm, nghỉ không lương và tăng ca được thống kê riêng, không cộng vào giờ công thường. Đây chưa phải giờ hưởng lương.',
      'Ngày lễ hoặc nghỉ phép hết thời gian phải làm, không có chấm công và không xung đột: 0 phút công. Thiếu/sai giờ hoặc xung đột: chưa xác định, không cộng vào tổng.',
    ],
  },
  {
    id: 'rounding',
    title: 'Đi trễ, về sớm & làm tròn 15 phút',
    expression:
      'Giây trễ mỗi khoảng = max(0, min(Giờ vào, Cuối khoảng) − Đầu khoảng)\nGiây về sớm mỗi khoảng = max(0, Cuối khoảng − max(Giờ ra, Đầu khoảng))\nPhút làm tròn = ceil(Tổng giây / 900) × 15',
    explanation:
      'Tính trên các khoảng còn phải làm sau khi áp dụng phép. Cộng giây trễ của các khoảng rồi làm tròn; cộng giây về sớm rồi làm tròn riêng. ceil nghĩa là làm tròn lên số nguyên.',
    example:
      'Trễ 1 giây → 15 phút; trễ đúng 15 phút → 15 phút; trễ 15 phút 1 giây → 30 phút. Trễ 7 phút và về sớm 10 phút → trừ tổng 30 phút.',
    notes: [
      'Phút có phép/chưa có phép chỉ phục vụ đối soát. Không dùng chúng để cộng lại thay thế công thức trừ công chuẩn.',
    ],
  },
  {
    id: 'actual-time',
    title: 'Thời gian làm thực tế trong ca',
    expression: 'Giây thực tế = Σ max(0, min(Giờ ra, Cuối khoảng) − max(Giờ vào, Đầu khoảng))',
    explanation:
      'Lấy phần giao giữa giờ vào/ra và từng khoảng còn phải làm, cộng theo giây. Bỏ khoảng nghỉ và thời gian ngoài ca. Giá trị này khác giờ tính công sau làm tròn.',
    example:
      'Vào 08:07, ra 17:20 với ca 08:00–12:00, 13:30–17:30 → thực tế 463 phút (7 giờ 43 phút), nhưng tính công 450 phút (7 giờ 30 phút).',
    notes: ['Một cặp giờ vào/ra không ghi được các lần rời chỗ và quay lại giữa ca.'],
  },
  {
    id: 'permission-time',
    title: 'Thời lượng xin đi trễ / về sớm',
    expression:
      'Xin đi trễ = Giờ dự kiến đến − Giờ bắt đầu buổi\nXin về sớm = Giờ kết thúc buổi − Giờ dự kiến về\nGiây thiếu có phép = min(Giây thiếu thực tế của buổi, Phút được phép × 60)\nPhút thiếu chưa có phép = ceil(max(0, Tổng giây thiếu − Tổng giây có phép) / 900) × 15',
    explanation:
      'Thời lượng xin phép lấy từ ca của ngày đã chọn. Số phút xin được làm tròn lên bước 15 phút để tham chiếu; phần bao phủ có phép dùng thời lượng gốc của đơn đã duyệt hợp lệ. Đối soát trễ và về sớm riêng.',
    example:
      'Ca 13:00–15:00, được phép đến 13:08 nhưng thực tế đến 13:16, ra đúng giờ → trừ 30 phút, còn 90 phút công. Có phép 8 phút; thiếu chưa có phép 8 phút được đối soát thành 15 phút.',
    notes: [
      'Duyệt đơn không cộng bù giờ công và không trừ phép năm. Đơn xung đột ca, phép hoặc ngày lễ không được dùng làm phần bao phủ có phép.',
    ],
  },
  {
    id: 'overtime-time',
    title: 'OT đã duyệt & OT ghi nhận',
    expression:
      'Phút OT đã duyệt = Thời lượng đăng ký được duyệt\nPhút OT ghi nhận = floor(max(0, min(Giờ ra OT, Kết thúc được duyệt) − max(Giờ vào OT, Bắt đầu được duyệt)) / 60 giây)',
    explanation:
      'Chỉ tính phần giao giữa thời gian chấm OT và khoảng được duyệt. floor nghĩa là lấy số phút nguyên, bỏ giây lẻ. OT không làm tròn theo bước 15 phút của công thường.',
    example:
      'Được duyệt 18:00–20:00 (120 phút), bắt đầu 18:10, kết thúc 20:05 → ghi nhận 110 phút.',
    notes: [
      'Đơn đã duyệt thiếu giờ vào/ra OT chưa có số phút ghi nhận. Tổng OT trong báo cáo chỉ cộng các giá trị đã có.',
      'OT qua đêm được tổng hợp theo ngày bắt đầu đăng ký. Chưa có hệ số nhân hoặc phép tính tiền tăng ca.',
    ],
  },
  {
    id: 'leave-days',
    title: 'Số ngày nghỉ: đơn phép và bảng công',
    expression:
      'Đơn cả ngày: Số ngày = Ngày kết thúc − Ngày bắt đầu + 1\nĐơn nửa ngày: Số ngày = 0,5\nGiờ phép trên bảng công = Tổng phút các khoảng ca được phép đã duyệt bao phủ / 60',
    explanation:
      'Đơn phép hiện tính theo ngày lịch, chưa loại cuối tuần/ngày lễ. Bảng công áp dụng phép theo ca từng ngày; ngày lễ chung hoặc ngày không có ca không tự quy thành một ngày công hay 8 giờ phép.',
    example:
      'Đơn cả ngày từ thứ Sáu đến Chủ nhật = 3 ngày phép theo đơn. Nếu chỉ thứ Sáu có ca 8 giờ thì giờ phép áp dụng trong bảng công là 8 giờ, không phải 24 giờ.',
    notes: [
      'Ngày phép trên bảng công cộng 1 cho đơn cả ngày, 0,5 cho đơn nửa ngày đã được áp dụng trong từng ngày; gồm cả phép năm và nghỉ không lương.',
      'Phút phép của một buổi phụ thuộc độ dài buổi đó, không luôn bằng 4 giờ.',
    ],
  },
  {
    id: 'leave-balance',
    title: 'Số dư phép năm & hoàn phép',
    expression:
      'Số ngày = Số đơn vị nửa ngày / 2\nPhép còn lại = Phép được hưởng − Phép đã dùng\nPhép hoàn khi duyệt điều chỉnh = Số ngày cũ − Số ngày còn nghỉ',
    explanation:
      'Hệ thống hiện khởi tạo 24 đơn vị nửa ngày, tương đương 12 ngày phép năm. Phép đã dùng tăng khi đơn phép năm được duyệt, bao gồm cả ngày nghỉ sắp tới; chỉ hoàn phần giảm khi điều chỉnh được duyệt.',
    example:
      'Được hưởng 12 ngày, đã duyệt 3 ngày → còn 9 ngày. Điều chỉnh đơn 3 ngày thành 1 ngày được duyệt → hoàn 2 ngày, còn 11 ngày.',
    notes: [
      'Đây là quy tắc đang được cấu hình trong ứng dụng, chưa có tính phép theo thâm niên hoặc tỷ lệ tháng làm việc.',
      'Nghỉ không lương và đơn chờ duyệt không trừ số dư phép năm.',
    ],
  },
  {
    id: 'report-totals',
    title: 'Chỉ số tổng hợp & cảnh báo bảng công',
    expression:
      'Nhân viên trong kỳ = Số nhân viên có dòng dữ liệu\nNhân viên cần kiểm tra = Số người có ít nhất 1 ngày cần kiểm tra\nGiờ tính công đã xác định = Tổng phút công khác rỗng / 60\nTăng ca ghi nhận = Tổng phút OT ghi nhận / 60',
    explanation:
      'Ngày cần kiểm tra là ngày thiếu ghi nhận, thiếu giờ ra, giờ không hợp lệ, xung đột nguồn/đơn hoặc có đơn phép, đi trễ/về sớm, OT đang chờ. Một ngày có nhiều vấn đề chỉ được đếm một lần trong tổng ngày cần kiểm tra.',
    example:
      'Hai ngày có 450 và 480 phút công, một ngày thiếu giờ ra → tổng đã xác định là 930 phút = 15 giờ 30 phút, kèm 1 ngày chưa tính được công.',
    notes: [
      'Đơn chờ được đếm duy nhất theo loại và mã đơn trong phạm vi nhân viên/kỳ; một đơn kéo dài nhiều ngày không bị đếm lặp.',
      'Ngày chưa bắt đầu hoặc đang làm việc có thể chưa tính được công nhưng không tự bị đánh dấu thiếu ghi nhận. Ngày không có ca được tách riêng.',
      'Không có giá trị công nào đã xác định thì hiển thị dấu gạch ngang. Giá trị 0 đã xác định khác với dữ liệu còn thiếu.',
      'Ngày đi trễ/về sớm = số ngày có phút trễ hoặc phút về sớm lớn hơn 0. Ngày đã hoàn tất = số ngày có trạng thái Đã kết thúc.',
    ],
  },
]

export const guides = [
  {
    id: 'getting-started',
    title: 'Bắt đầu sử dụng',
    icon: 'user',
    path: '/account',
    steps: [
      'Đăng nhập bằng tài khoản được cấp. Nếu nhận email mời, mở liên kết kích hoạt và thiết lập mật khẩu trước.',
      'Mở Hồ sơ của tôi từ menu tài khoản ở góc trên để kiểm tra thông tin cá nhân, công việc và vai trò.',
      'Kiểm tra ca trong Chấm công của tôi, xem số dư phép theo năm trong hồ sơ. Liên hệ HR nếu thiếu hồ sơ hoặc ca làm việc.',
    ],
    note: 'Các mục quản lý dành cho HR/ADMIN. Vai trò Quản lý (MANAGER) không tự có quyền xét duyệt.',
  },
  {
    id: 'daily-attendance',
    title: 'Chấm công hằng ngày',
    icon: 'clock',
    path: '/attendance',
    steps: [
      'Kết nối mạng công ty, mở Tổng quan hoặc Chấm công của tôi và kiểm tra ca/ngày công đang hiển thị.',
      'Bấm Ghi nhận giờ vào khi bắt đầu; bấm Ghi nhận giờ ra khi kết thúc trong khoảng cho phép của ca.',
      'Chọn kỳ để xem từng ngày và các chỉ số. Nếu thiếu hoặc sai giờ, mở Bổ sung / Điều chỉnh để gửi đơn sau khi ca kết thúc.',
    ],
    note: 'Ca qua đêm vẫn ghi vào ngày bắt đầu ca. Giờ công chỉ xác định khi đủ dữ liệu hợp lệ; giờ nghỉ giữa ca và OT không được cộng vào công thường.',
  },
  {
    id: 'request-leave',
    title: 'Xin nghỉ & điều chỉnh phép',
    icon: 'calendar',
    path: '/leave',
    steps: [
      'Mở Yêu cầu của tôi → Nghỉ phép của tôi; chọn phép năm/nghỉ không lương, ngày, cả ngày/buổi và nhập lý do.',
      'Gửi đơn, theo dõi trạng thái. Mở chi tiết để xem lịch sử hoặc rút đơn đang chờ.',
      'Với đơn đã duyệt, mở Điều chỉnh phép đã duyệt để yêu cầu hủy hoặc rút ngắn; chờ HR/ADMIN khác xét duyệt.',
    ],
    note: 'Số ngày của đơn hiện gồm cả cuối tuần và ngày lễ trong khoảng đã chọn. Phép năm chỉ bị trừ khi được duyệt; điều chỉnh chỉ hoàn phép sau khi được duyệt.',
  },
  {
    id: 'request-permission',
    title: 'Xin đi trễ / về sớm',
    icon: 'mail',
    path: '/attendance/requests',
    steps: [
      'Mở Yêu cầu của tôi → Xin đi trễ / về sớm. Chọn ngày để tải ca thực tế.',
      'Chọn loại yêu cầu, buổi, giờ dự kiến và lý do; kiểm tra thời lượng rồi gửi.',
      'Theo dõi, sửa/rút khi còn chờ và ngày chưa qua. Nếu ca thay đổi, tải lại và sửa đơn theo ca mới.',
    ],
    note: 'Đơn được duyệt ghi nhận có phép, vẫn trừ công theo bước làm tròn 15 phút. Nghỉ hết buổi cần dùng đơn nghỉ phép.',
  },
  {
    id: 'request-overtime',
    title: 'Đăng ký & ghi nhận tăng ca',
    icon: 'clock',
    path: '/attendance/overtime',
    steps: [
      'Mở Yêu cầu của tôi → Tăng ca (OT); chọn khoảng thời gian ngoài giờ làm thường và nhập lý do.',
      'Gửi đăng ký đủ sớm để HR/ADMIN xét duyệt trước giờ bắt đầu.',
      'Sau khi được duyệt, kết nối mạng công ty và bấm Bắt đầu OT trong khoảng đã duyệt; bấm Kết thúc OT khi xong.',
    ],
    note: 'Số phút được duyệt là giới hạn đăng ký; số phút ghi nhận chỉ tính phần thời gian chấm OT nằm trong giới hạn đó.',
  },
  {
    id: 'review-requests',
    title: 'Xét duyệt yêu cầu',
    icon: 'shield',
    path: '/leave/inbox',
    restricted: true,
    steps: [
      'Mở Duyệt nghỉ phép hoặc Duyệt đi trễ / về sớm trên menu. Với OT và điều chỉnh công, mở Hàng chờ duyệt tại trang tương ứng.',
      'Đọc lý do, ngày/ca và dữ liệu đề nghị. Với nghỉ phép đã duyệt, chọn bộ lọc Điều chỉnh phép đã duyệt để xử lý yêu cầu thay đổi.',
      'Duyệt hoặc từ chối; nhập lý do khi từ chối. Nếu dữ liệu đã thay đổi, tải lại rồi kiểm tra trước khi xử lý.',
    ],
    note: 'Không tự xét duyệt đơn của mình. Nhãn số trên menu giúp theo dõi các yêu cầu đang chờ.',
  },
  {
    id: 'manage-schedule',
    title: 'Thiết lập ca & phân công',
    icon: 'calendar',
    path: '/attendance/schedules',
    restricted: true,
    steps: [
      'Tạo hoặc kiểm tra mẫu trong Ca làm việc: các khoảng làm, cửa sổ chấm công và tùy chọn qua đêm.',
      'Mở Phân công & lịch làm việc; chọn phạm vi, ca, các thứ và khoảng ngày hiệu lực.',
      'Xem trước, kiểm tra các xung đột rồi áp dụng. Nếu đổi lựa chọn, xem trước lại; xem lịch sử để kiểm tra kết quả.',
    ],
    note: 'Lịch riêng được ưu tiên so với lịch công ty trong ngày có hiệu lực. Sửa ca không tự thay dữ liệu ca đã lưu của ngày công cũ.',
  },
  {
    id: 'read-report',
    title: 'Đọc & đối soát bảng công',
    icon: 'grid',
    path: '/attendance/reports',
    restricted: true,
    steps: [
      'Chọn Từ ngày, Đến ngày, nhân viên/phòng ban rồi bấm Xem bảng công để tải kỳ.',
      'Xem tổng hợp, dùng bộ lọc Cần kiểm tra hoặc Công chưa đầy đủ, bấm tên nhân viên để mở từng ngày.',
      'Nếu phép hoặc lịch nghỉ đã đổi, cập nhật phép/lịch ở ngày đã ghi nhận. Giờ sai cần xử lý qua đơn điều chỉnh công.',
      'Bấm Xuất CSV theo ngày để tải toàn bộ kỳ đã tải. Bộ lọc tìm tên trong bảng không thu hẹp file CSV.',
    ],
    note: 'Bảng công đang tạm tính. Tổng chỉ cộng ngày đã xác định, tách riêng phép và OT; không dùng trực tiếp như bảng lương đã chốt.',
  },
  {
    id: 'manage-people',
    title: 'Quản lý nhân sự & tài khoản',
    icon: 'user',
    path: '/employees',
    restricted: true,
    steps: [
      'Chuẩn bị Phòng ban và Chức danh, sau đó tạo hồ sơ Nhân viên với thông tin cá nhân và công việc.',
      'Gán phòng ban, chức danh, người quản lý; kiểm tra ngày vào làm và trạng thái công việc.',
      'Admin cấp tài khoản từ hồ sơ hoặc mở Tạo tài khoản cho hồ sơ đã có. HR/Admin theo dõi kết quả và trạng thái email kích hoạt, gửi lại nếu cần.',
    ],
    note: 'Trạng thái công việc của nhân viên không đồng nghĩa với trạng thái tài khoản đăng nhập.',
  },
  {
    id: 'manage-calendar',
    title: 'Công bố lịch nghỉ & sự kiện',
    icon: 'calendar',
    path: '/calendar-events',
    restricted: true,
    steps: [
      'Mở Quản lý lịch, tạo sự kiện và chọn loại, thời gian, phạm vi áp dụng.',
      'Lưu bản nháp, kiểm tra thông tin rồi công bố. Mở lại sự kiện để cập nhật hoặc hủy khi cần.',
      'Kiểm tra kết quả ở Lịch nghỉ & sự kiện. Với ngày nghỉ chung đã thay đổi, đối soát các ngày công đã ghi nhận.',
    ],
    note: 'Để tác động vào công thường, ngày nghỉ cần được công bố, là sự kiện nghỉ cả ngày và áp dụng toàn công ty.',
  },
]

export const helpQuestions = [
  [
    'Vì sao giờ tính công thấp hơn giờ có mặt?',
    'Hệ thống chỉ tính trong các khoảng làm việc của ca, bỏ giờ nghỉ và giờ ngoài ca. Đi trễ và về sớm được làm tròn riêng lên bước 15 phút. OT phải đăng ký, được duyệt và chấm giờ riêng.',
  ],
  [
    'Đã được duyệt đi trễ, vì sao vẫn trừ công?',
    'Duyệt đơn ghi nhận phần đi trễ/về sớm là có phép, không cộng bù giờ làm. Công thức trừ công vẫn giữ nguyên và số dư phép năm không bị trừ.',
  ],
  [
    'Dấu “—” và 0 giờ khác nhau thế nào?',
    'Dấu gạch ngang là chưa xác định vì chưa đủ dữ liệu. 0 giờ là kết quả đã tính được, chẳng hạn ngày nghỉ lễ không có chấm công. Ngày không có ca được ghi riêng.',
  ],
  [
    'Phép đã duyệt hoặc điều chỉnh rồi nhưng bảng công chưa đổi?',
    'Ngày công đã ghi nhận giữ dữ liệu phép/lịch tại thời điểm lưu. Nhờ HR mở chi tiết ngày trong Bảng công và cập nhật phép/lịch.',
  ],
  [
    'Không chấm công được hoặc dữ liệu bị lỗi?',
    'Kiểm tra mạng công ty, ca và cửa sổ chấm công. Khi kết nối gián đoạn, tải lại để kiểm tra kết quả trước khi thử lại. Mở Kết nối dịch vụ; nếu vẫn lỗi, báo HR kèm ngày và thông báo lỗi.',
  ],
  [
    'Không thấy mục quản lý hoặc không có quyền truy cập?',
    'Các mục quản lý yêu cầu HR hoặc ADMIN. Liên hệ quản trị viên kiểm tra quyền; vai trò MANAGER không tự được quyền xét duyệt.',
  ],
]
