import { auth } from '@/auth/session.js'

export const employeeStatuses = {
    ACTIVE: 'Đang làm việc',
    INACTIVE: 'Tạm ngừng',
    PROBATION: 'Thử việc',
    RESIGNED: 'Đã nghỉ việc',
    TERMINATED: 'Chấm dứt hợp đồng',
}
export const accountStatuses = {
    NOT_CREATED: 'Chưa có tài khoản',
    PENDING_ACTIVATION: 'Chờ kích hoạt',
    ACTIVE: 'Đang hoạt động',
    DISABLED: 'Đã vô hiệu hóa',
}
export const requestStatuses = {
    PENDING: 'Đang xử lý',
    SUCCEEDED: 'Đã cấp tài khoản',
    FAILED: 'Cấp tài khoản thất bại',
}
export const fullName = (employee) => `${employee.lastName} ${employee.firstName}`
export const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
export const hrm = {
    list: (resource, page = 0, size = 20) =>
        auth.request(`/api/v1/${resource}?page=${page}&size=${size}`),
    get: (resource, id) => auth.request(`/api/v1/${resource}/${encodeURIComponent(id)}`),
    save: (resource, id, body) =>
        auth.request(`/api/v1/${resource}${id ? `/${encodeURIComponent(id)}` : ''}`, {
            method: id ? 'PUT' : 'POST',
            body: JSON.stringify(body),
        }),
    status: (id, status) =>
        auth.request(`/api/v1/employees/${id}/status`, {
            method: 'PATCH',
            body: JSON.stringify({ status }),
        }),
    provision: (id, email, key) =>
        auth.request(`/api/v1/employees/${id}/account-requests`, {
            method: 'POST',
            headers: { 'Idempotency-Key': key },
            body: JSON.stringify({ email }),
        }),
    request: (id, requestId) =>
        auth.request(`/api/v1/employees/${id}/account-requests/${encodeURIComponent(requestId)}`),
}
