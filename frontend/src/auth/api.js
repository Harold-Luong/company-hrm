export class ApiError extends Error {
  constructor(status, message, retryAfter = null) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.retryAfter = retryAfter
  }
}
const messages = {
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
  if (error.status === 400) return 'Thông tin không hợp lệ. Vui lòng kiểm tra lại các trường.'
  return 'Không thể kết nối dịch vụ xác thực. Vui lòng thử lại sau.'
}
export async function authRequest(path, init = {}) {
  let response
  try {
    response = await fetch(`/api/v1/auth${path}`, {
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
        : response.statusText
    throw new ApiError(response.status, message, response.headers.get('Retry-After'))
  }
  if (!data || typeof data !== 'object') throw new ApiError(502, 'Invalid server response')
  return data
}
