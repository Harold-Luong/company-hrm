<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'
import { hrm } from '@/hrm/api.js'
import { serviceError } from '@/auth/api.js'
import { formatDate } from '@/auth/format.js'

const props = defineProps({ employee: { type: Object, required: true } })
const invitation = ref(null)
const operation = ref('')
const error = ref('')
const message = ref('')
const remaining = ref(0)
const canResend = computed(() => props.employee.accountStatus === 'PENDING_ACTIVATION')
let cooldownTimer
let disposed = false
const statuses = {
    PENDING: 'Email đang chờ gửi',
    SENT: 'Dịch vụ email đã chấp nhận gửi',
    FAILED: 'Gửi email thất bại',
    CANCELLED: 'Lời mời đã bị hủy',
}
function cooldown(seconds) {
    clearInterval(cooldownTimer)
    const until = Date.now() + seconds * 1000
    remaining.value = seconds
    cooldownTimer = setInterval(() => {
        remaining.value = Math.max(0, Math.ceil((until - Date.now()) / 1000))
        if (!remaining.value) clearInterval(cooldownTimer)
    }, 1000)
}
function failure(cause, sending) {
    if (cause.status === 429) {
        const seconds = Number(cause.retryAfter)
        cooldown(Number.isFinite(seconds) && seconds > 0 ? Math.ceil(seconds) : 60)
        return 'Vui lòng chờ hết thời gian chờ trước khi thử lại.'
    }
    if (cause.status === 404)
        return sending
            ? 'Chưa tìm thấy tài khoản Auth của nhân viên này.'
            : 'Chưa có lời mời kích hoạt cho nhân viên này.'
    if (cause.status === 409)
        return 'Tài khoản không còn ở trạng thái chờ kích hoạt. Hãy tải lại hồ sơ.'
    if (cause.status === 503)
        return cause.message === 'Account activation is disabled'
            ? 'Tính năng email kích hoạt chưa được bật. Vui lòng liên hệ quản trị viên.'
            : serviceError(cause)
    return serviceError(cause)
}
async function request(sending = false) {
    if (operation.value || disposed || (sending && (!canResend.value || remaining.value))) return
    operation.value = sending ? 'send' : 'check'
    error.value = ''
    message.value = ''
    try {
        const data = await (sending
            ? hrm.resendActivationInvitation(props.employee.id)
            : hrm.activationInvitation(props.employee.id))
        if (disposed) return
        invitation.value = data
        if (sending) {
            cooldown(60)
            message.value =
                'Đã xếp hàng email kích hoạt mới và thu hồi liên kết cũ. Email chưa được xác nhận gửi thành công.'
        }
    } catch (cause) {
        if (disposed) return
        invitation.value = null
        error.value = failure(cause, sending)
        if (
            sending &&
            (cause.status === 0 || cause.status >= 500) &&
            cause.message !== 'Account activation is disabled'
        ) {
            message.value =
                'Chưa xác nhận được kết quả gửi. Hãy kiểm tra email trước khi gửi lại để tránh thu hồi một liên kết vừa tạo.'
        }
    } finally {
        operation.value = ''
    }
}
onBeforeUnmount(() => {
    disposed = true
    clearInterval(cooldownTimer)
})
</script>

<template>
    <section class="activation-invitation request-lookup" :aria-busy="!!operation">
        <h3>Email kích hoạt tài khoản</h3>
        <p class="small muted">
            Email được gửi tới địa chỉ đăng nhập của tài khoản trong Auth, có thể khác email hồ sơ nhân
            viên. Gửi lại sẽ vô hiệu hóa liên kết cũ. Mặc định cần chờ 60 giây giữa các lần gửi.
        </p>
        <button class="button button-secondary full-width" type="button"
            :disabled="!!operation || !canResend || remaining > 0" @click="request(true)">
            {{
                operation === 'send'
                    ? 'Đang xếp hàng email…'
                    : remaining > 0
                        ? `Gửi lại sau ${remaining} giây`
                        : 'Gửi lại email kích hoạt'
            }}
        </button>
        <p v-if="!canResend" class="field-help">Chỉ gửi lại email khi tài khoản đang chờ kích hoạt.</p>
        <button class="button button-secondary full-width" type="button" :disabled="!!operation" @click="request()">
            {{ operation === 'check' ? 'Đang kiểm tra email…' : 'Kiểm tra email kích hoạt' }}
        </button>
        <div v-if="error" class="alert alert-error" role="alert">{{ error }}</div>
        <p v-if="message" class="info-strip" role="status">{{ message }}</p>
        <div v-if="invitation" class="small" role="status" aria-live="polite">
            <strong>{{ statuses[invitation.deliveryStatus] || invitation.deliveryStatus }}</strong>
            <p v-if="invitation.deliveryStatus === 'PENDING'">
                Bấm kiểm tra email để cập nhật kết quả gửi.
            </p>
            <p v-if="invitation.deliveryStatus === 'SENT'">
                Resend đã chấp nhận email; điều này chưa xác nhận email đã vào hộp thư hoặc tài khoản đã
                kích hoạt.
            </p>
            <p v-if="invitation.deliveryStatus === 'FAILED'">
                Vui lòng liên hệ quản trị viên hoặc gửi lại khi hết thời gian chờ.
            </p>
            <p>
                Mã lời mời: <code>{{ invitation.invitationId }}</code>
            </p>
            <p v-if="invitation.expiresAt">Liên kết hết hạn: {{ formatDate(invitation.expiresAt) }}</p>
            <p v-if="invitation.sentAt">
                Dịch vụ email chấp nhận lúc: {{ formatDate(invitation.sentAt) }}
            </p>
            <p v-if="invitation.attempts != null">Số lần thử gửi: {{ invitation.attempts }}</p>
            <p v-if="invitation.errorCode" class="field-error">
                Mã lỗi gửi email: {{ invitation.errorCode }}
            </p>
        </div>
    </section>
</template>

<style scoped>
.activation-invitation {
    display: grid;
    gap: 12px;
    overflow-wrap: anywhere;
}
</style>
