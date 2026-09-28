export class ApiError extends Error {
  constructor(status, message, retryAfter = null) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.retryAfter = retryAfter
  }
}
const messages = {
  'Department code already exists': 'Mã phòng ban đã tồn tại.',
  'Position code already exists': 'Mã chức danh đã tồn tại.',
  'Employee with this employeeCode already exists': 'Mã nhân viên đã tồn tại.',
  'Employee with this email already exists': 'Email nhân viên đã tồn tại.',
  'dateOfBirth must be before hireDate': 'Ngày sinh phải trước ngày vào làm.',
  'managerId must not create a cycle in the manager hierarchy':
    'Quan hệ quản lý không được tạo thành vòng lặp.',
  'Account provisioning is disabled':
    'Dịch vụ cấp tài khoản chưa được bật. Vui lòng liên hệ quản trị viên.',
  'An account request is already pending':
    'Nhân viên đã có yêu cầu đang xử lý. Hãy tra cứu bằng mã yêu cầu hiện có.',
  'Idempotency-Key was used with different data':
    'Mã gửi đã dùng với dữ liệu khác. Vui lòng kiểm tra lại yêu cầu.',
  'Invalid email or password': 'Email hoặc mật khẩu không chính xác.',
  'User is inactive': 'Tài khoản đã bị vô hiệu hóa. Vui lòng liên hệ quản trị viên.',
  'Email already exists': 'Email này đã có tài khoản.',
  'Employee already has an account': 'UUID liên kết này đã có tài khoản.',
  'Invalid refresh token': 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.',
  'Access is denied': 'Bạn không có quyền thực hiện thao tác này.',
}
export function errorMessage(error) {
  if (!(error instanceof ApiError)) return 'Có lỗi xảy ra. Vui lòng thử lại.'
  if (messages[error.message]) return messages[error.message]
  if (error.status === 429) {
    const seconds = Number(error.retryAfter)
    return Number.isFinite(seconds) && seconds > 0
      ? `Bạn đã thử quá nhiều lần. Vui lòng thử lại sau ${Math.ceil(seconds / 60)} phút.`
      : 'Bạn đã thử quá nhiều lần. Vui lòng chờ một lúc rồi thử lại.'
  }
  if (error.status === 401) return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.'
  if (error.status === 403) return 'Bạn không có quyền thực hiện thao tác này.'
  if (error.status === 404) return 'Không tìm thấy dữ liệu. Vui lòng tải lại danh sách.'
  if (error.status === 409)
    return 'Dữ liệu bị trùng hoặc tham chiếu đã thay đổi. Vui lòng kiểm tra lại.'
  if (error.status === 400) return 'Thông tin không hợp lệ. Vui lòng kiểm tra lại các trường.'
  return 'Không thể kết nối dịch vụ xác thực. Vui lòng thử lại sau.'
}
export function authRequest(path, init = {}) {
  return apiRequest(`/api/v1/auth${path}`, init)
}
export async function apiRequest(path, init = {}) {
  let response
  try {
    response = await fetch(path, {
      ...init,
      headers: {
        Accept: 'application/json',
        ...(init.body ? { 'Content-Type': 'application/json' } : {}),
        ...init.headers,
      },
      signal: AbortSignal.timeout(15_000),
      cache: 'no-store',
    })
  } catch {
    throw new ApiError(0, 'Network unavailable')
  }
  const data = await response.json().catch(() => null)
  if (!response.ok) {
    const message =
      data && typeof data === 'object' && 'message' in data
        ? String(data.message)
        : data?.detail || response.statusText
    throw new ApiError(response.status, message, response.headers.get('Retry-After'))
  }
  if (!data || typeof data !== 'object') throw new ApiError(502, 'Invalid server response')
  return data
}

export function serviceError(error) {
  const message = errorMessage(error)
  return message.replace('dịch vụ xác thực', 'dịch vụ')
}
