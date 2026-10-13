<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { hrm, uuidPattern, requestStatuses, accountStatuses } from '@/hrm/api.js'
import { auth } from '@/auth/session.js'
import { accountCreationRoles } from '@/auth/navigation.js'
import { serviceError } from '@/auth/api.js'
import ActivationInvitationPanel from '@/components/ActivationInvitationPanel.vue'
import {
  trackAccountRequest,
  subscribeAccountResults,
  provisioningFailures as failures,
} from '@/notifications/accountResults.js'
const props = defineProps({ employee: { type: Object, required: true } })
const emit = defineEmits(['updated'])
const requesterId = String(auth.state.user.id)
const storageKey = `company-hrm.provisioning.${requesterId}.${props.employee.id}`
const email = ref(props.employee.personalEmail ?? '')
const lookupId = ref('')
const attempt = ref(null)
const result = ref(null)
const busy = ref(false)
const operation = ref('')
const checkedAt = ref('')
const error = ref('')
const note = ref('')
let timer
let disposed = false
let polls = 0
let stopAccountResults
function persist() {
  try {
    sessionStorage.setItem(storageKey, JSON.stringify(attempt.value))
  } catch {
    note.value = 'Không thể lưu mã yêu cầu trong tab. Hãy ghi lại mã để tra cứu sau.'
  }
}
function accept(data, submitted = false) {
  if (disposed) {
    // The POST can finish after navigation. Keep its fallback tracking alive in the layout.
    if (submitted && attempt.value && String(auth.state.user?.id ?? '') === requesterId) {
      attempt.value.requestId = data.requestId
      persist()
      trackAccountRequest(props.employee.id, data)
    }
    return
  }
  if (
    result.value?.requestId === data.requestId &&
    result.value.provisioningStatus !== 'PENDING' &&
    data.provisioningStatus === 'PENDING'
  )
    return
  clearTimeout(timer)
  result.value = data
  lookupId.value = data.requestId
  if (submitted && attempt.value) {
    attempt.value.requestId = data.requestId
    persist()
  }
  emit('updated', data.accountStatus)
  const owned = submitted || attempt.value?.requestId === data.requestId
  if (owned) trackAccountRequest(props.employee.id, data)
  if (!owned && data.provisioningStatus === 'PENDING' && polls < 30) {
    timer = setTimeout(() => {
      polls++
      check(true)
    }, 3000)
  } else if (!owned && data.provisioningStatus === 'PENDING') {
    note.value = 'Yêu cầu vẫn đang xử lý. Bạn có thể kiểm tra lại sau; không cần gửi yêu cầu mới.'
  }
}
async function send() {
  if (
    !auth.hasRole(accountCreationRoles) ||
    busy.value ||
    result.value?.provisioningStatus === 'PENDING'
  )
    return
  clearTimeout(timer)
  error.value = ''
  note.value = ''
  busy.value = true
  operation.value = 'send'
  polls = 0
  try {
    if (!attempt.value) {
      attempt.value = {
        key: crypto.randomUUID(),
        email: email.value.trim().toLowerCase(),
        requestId: null,
      }
      persist()
    }
    accept(await hrm.provision(props.employee.id, attempt.value.email, attempt.value.key), true)
  } catch (cause) {
    error.value = serviceError(cause)
    if ([400, 403, 404, 409].includes(cause.status)) {
      attempt.value = null
      sessionStorage.removeItem(storageKey)
    } else
      note.value =
        'Chưa xác nhận được kết quả gửi. Thử lại sẽ dùng cùng mã gửi để tránh tạo yêu cầu trùng.'
  } finally {
    busy.value = false
    operation.value = ''
  }
}
async function check(automatic = false) {
  if (busy.value || disposed) return
  clearTimeout(timer)
  if (!automatic) {
    polls = 0
    note.value = ''
    result.value = null
    checkedAt.value = ''
  }
  if (!uuidPattern.test(lookupId.value.trim())) {
    error.value = 'Mã yêu cầu phải là UUID hợp lệ.'
    return
  }
  error.value = ''
  busy.value = true
  operation.value = 'lookup'
  try {
    accept(await hrm.request(props.employee.id, lookupId.value.trim()))
    checkedAt.value = new Date().toLocaleTimeString('vi-VN')
  } catch (cause) {
    error.value =
      cause.status === 404
        ? 'Không tìm thấy yêu cầu cấp tài khoản của nhân viên này. Hãy kiểm tra lại mã yêu cầu.'
        : serviceError(cause)
  } finally {
    busy.value = false
    operation.value = ''
  }
}
function newRequest() {
  clearTimeout(timer)
  attempt.value = null
  result.value = null
  lookupId.value = ''
  error.value = ''
  note.value = ''
  checkedAt.value = ''
  sessionStorage.removeItem(storageKey)
}
onMounted(() => {
  stopAccountResults = subscribeAccountResults((data) => {
    if (data.employeeId !== props.employee.id) return
    emit('updated', data.accountStatus)
    if (data.requestId === lookupId.value || data.requestId === attempt.value?.requestId)
      accept(data)
  })
  try {
    const saved = JSON.parse(sessionStorage.getItem(storageKey))
    if (saved && uuidPattern.test(saved.key) && typeof saved.email === 'string') {
      attempt.value = saved
      email.value = saved.email
      if (saved.requestId && uuidPattern.test(saved.requestId)) {
        lookupId.value = saved.requestId
        check()
      } else
        note.value =
          'Có lần gửi chưa xác nhận kết quả trong tab này. Thử lại để lấy kết quả với cùng mã gửi.'
    }
  } catch {
    sessionStorage.removeItem(storageKey)
  }
})
onBeforeUnmount(() => {
  disposed = true
  clearTimeout(timer)
  stopAccountResults?.()
})
</script>
<template>
  <section class="panel create-form provisioning-panel" :aria-busy="busy">
    <p class="eyebrow">CẤP TÀI KHOẢN QUA KAFKA</p>
    <h2>Tài khoản nhân viên</h2>
    <p class="muted small">
      Yêu cầu được xử lý bất đồng bộ. Tài khoản mới có vai trò Nhân viên và chờ kích hoạt.
    </p>
    <p v-if="note" class="info-strip" role="status">{{ note }}</p>
    <p v-if="!auth.hasRole(accountCreationRoles)" class="muted small">
      Chỉ Admin được tạo tài khoản. Bạn có thể tra cứu kết quả và hỗ trợ kích hoạt tài khoản.
    </p>
    <form v-if="auth.hasRole(accountCreationRoles) && !result" @submit.prevent="send">
      <fieldset class="form-fields" :disabled="busy">
        <label for="provision-email">Email cấp tài khoản *</label
        ><input
          id="provision-email"
          v-model="email"
          type="email"
          required
          maxlength="255"
          :readonly="!!attempt"
        />
        <button
          type="submit"
          class="button button-primary full-width"
          :disabled="employee.accountStatus !== 'NOT_CREATED' && !attempt"
        >
          {{
            operation === 'send'
              ? 'Đang gửi…'
              : attempt
                ? 'Thử gửi lại'
                : 'Gửi yêu cầu cấp tài khoản'
          }}
        </button>
      </fieldset>
    </form>
    <form class="request-lookup" @submit.prevent="check()">
      <fieldset class="form-fields" :disabled="busy">
        <label for="request-id">Tra cứu mã yêu cầu</label
        ><input
          id="request-id"
          v-model="lookupId"
          required
          placeholder="UUID của yêu cầu đã gửi"
          :readonly="result?.provisioningStatus === 'PENDING'"
        />
        <button class="button button-secondary full-width" type="submit">
          {{ operation === 'lookup' ? 'Đang kiểm tra…' : 'Kiểm tra kết quả' }}
        </button>
      </fieldset>
    </form>
    <div v-if="error" class="alert alert-error" role="alert">{{ error }}</div>
    <p v-if="operation === 'lookup'" class="info-strip" role="status">Đang tra cứu yêu cầu…</p>
    <div v-if="result" class="request-result" role="status" aria-live="polite" aria-atomic="true">
      <p v-if="checkedAt" class="small muted">Đã nhận kết quả lúc {{ checkedAt }}.</p>
      <strong>{{ requestStatuses[result.provisioningStatus] || result.provisioningStatus }}</strong>
      <p class="small muted">{{ accountStatuses[result.accountStatus] || result.accountStatus }}</p>
      <p
        v-if="result.provisioningStatus === 'PENDING' && attempt?.requestId === result.requestId"
        class="small"
      >
        Yêu cầu đã được ghi nhận và đang chờ xử lý. Kết quả sẽ tự cập nhật; bạn có thể chuyển trang
        trong khi chờ.
      </p>
      <p v-else-if="result.provisioningStatus === 'PENDING'" class="small">
        Yêu cầu đã được ghi nhận và đang chờ xử lý. Kết quả tự cập nhật mỗi 3 giây, tối đa 30 lần.
      </p>
      <p
        v-if="
          result.provisioningStatus === 'SUCCEEDED' && result.accountStatus === 'PENDING_ACTIVATION'
        "
        class="small"
      >
        Tài khoản đã được tạo. Nhân viên cần mở email mời để đặt mật khẩu và kích hoạt.
      </p>
      <p v-if="result.errorCode" class="field-error">
        {{ failures[result.errorCode] || result.errorCode }}
      </p>
      <p class="small">
        Mã yêu cầu: <code>{{ result.requestId }}</code>
      </p>
      <button
        v-if="auth.hasRole(accountCreationRoles) && result.provisioningStatus === 'FAILED'"
        class="button button-secondary"
        :disabled="busy"
        @click="newRequest"
      >
        Chuẩn bị yêu cầu mới
      </button>
    </div>
    <p class="field-help">
      Dùng mã yêu cầu cấp tài khoản đã trả về khi gửi yêu cầu, không phải mã nhân viên hoặc mã lời
      mời kích hoạt.
    </p>
    <ActivationInvitationPanel :key="employee.id" :employee="employee" />
  </section>
</template>
