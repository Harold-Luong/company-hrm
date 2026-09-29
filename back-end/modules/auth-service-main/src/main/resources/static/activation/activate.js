// Keep the credential only in memory, remove it from the address bar/history immediately.
let token = new URLSearchParams(window.location.search).get('token') || ''
window.history.replaceState(null, '', window.location.pathname)
const form = document.getElementById('activation-form')
const fields = document.getElementById('fields')
const error = document.getElementById('error')
const success = document.getElementById('success')
const button = document.getElementById('submit')
function showError(message) {
    error.textContent = message
    error.hidden = false
}
if (!/^[A-Za-z0-9_-]{43}$/.test(token)) {
    fields.disabled = true
    showError('Liên kết kích hoạt không hợp lệ. Vui lòng mở đầy đủ liên kết trong email.')
}
form.addEventListener('submit', async (event) => {
    event.preventDefault()
    if (fields.disabled) return
    error.hidden = true
    const password = document.getElementById('password').value
    if (!password.trim() || password.length < 12 || new TextEncoder().encode(password).length > 72) {
        showError('Mật khẩu cần ít nhất 12 ký tự và không vượt quá 72 byte UTF-8.')
        return
    }
    if (password !== document.getElementById('confirm-password').value) {
        showError('Mật khẩu nhập lại chưa khớp.')
        return
    }
    fields.disabled = true
    button.textContent = 'Đang kích hoạt…'
    try {
        const response = await fetch('/api/v1/auth/activate', {
            method: 'POST', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ token, password }), cache: 'no-store', credentials: 'omit',
            signal: AbortSignal.timeout(20000),
        })
        if (response.ok) {
            token = ''
            form.reset()
            form.hidden = true
            success.textContent = 'Tài khoản đã được kích hoạt. Bạn có thể quay về Company HRM và đăng nhập bằng mật khẩu vừa đặt.'
            success.hidden = false
        } else {
            const data = await response.json().catch(() => ({}))
            showError(data.message === 'Invalid or expired activation token'
                ? 'Liên kết đã hết hạn, đã sử dụng hoặc bị thu hồi. Nếu bạn đã kích hoạt trước đó, hãy thử đăng nhập; nếu chưa, liên hệ HR để gửi lại lời mời.'
                : response.status === 429 ? 'Bạn thao tác quá nhanh. Vui lòng thử lại sau.'
                    : response.status >= 500 ? 'Dịch vụ kích hoạt chưa sẵn sàng. Vui lòng thử lại sau.'
                        : 'Không thể kích hoạt. Vui lòng kiểm tra liên kết và mật khẩu rồi thử lại.')
        }
    } catch {
        showError('Chưa nhận được kết quả. Bạn có thể thử đăng nhập bằng mật khẩu vừa đặt; nếu chưa được, hãy gửi lại biểu mẫu.')
    } finally {
        fields.disabled = false
        button.textContent = 'Đặt mật khẩu và kích hoạt'
    }
})
