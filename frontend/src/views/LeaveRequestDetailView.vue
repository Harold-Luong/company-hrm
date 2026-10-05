<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { auth } from '@/auth/session.js'
import { leave } from '@/leave/api.js'
import { hrm, fullName } from '@/hrm/api.js'
import {
  leaveTypes,
  leaveStatuses,
  leavePeriods,
  leaveReviewRoles,
  historyActions,
  dateLabel,
  timeLabel,
  leaveError,
} from '@/leave/helpers.js'
const props = defineProps({ id: { type: String, required: true } })
const request = ref(null)
const history = ref([])
const historyError = ref('')
const employeeName = ref('')
const note = ref('')
const busy = ref(false)
const saving = ref(false)
const error = ref('')
const success = ref('')
const stale = ref(false)
const isReviewer = computed(() => auth.hasRole(leaveReviewRoles))
const own = computed(
  () =>
    request.value &&
    (request.value.employeeId === auth.state.user?.employeeId ||
      request.value.requesterUserId === String(auth.state.user?.id)),
)
let generation = 0
onBeforeUnmount(() => generation++)
async function load() {
  const current = ++generation
  busy.value = true
  error.value = ''
  success.value = ''
  request.value = null
  history.value = []
  historyError.value = ''
  employeeName.value = ''
  try {
    const data = await leave.get(props.id)
    if (current !== generation) return
    request.value = data
    stale.value = false
    const results = await Promise.allSettled([
      leave.history(props.id),
      isReviewer.value ? hrm.get('employees', data.employeeId) : Promise.resolve(null),
    ])
    if (current !== generation) return
    if (results[0].status === 'fulfilled') history.value = results[0].value
    else historyError.value = 'Chưa tải được lịch sử xử lý. Vui lòng tải lại.'
    if (results[1].status === 'fulfilled' && results[1].value)
      employeeName.value = fullName(results[1].value)
  } catch (cause) {
    if (current === generation) error.value = leaveError(cause)
  } finally {
    if (current === generation) busy.value = false
  }
}
async function decide(action) {
  if (saving.value || busy.value || stale.value) return
  if (action === 'reject' && !note.value.trim()) {
    error.value = 'Vui lòng nhập lý do từ chối.'
    return
  }
  saving.value = true
  const current = generation
  const requestId = props.id
  error.value = ''
  success.value = ''
  try {
    const updated = await leave.decide(requestId, action, request.value.version, note.value.trim())
    if (current !== generation) return
    request.value = updated
    success.value = action === 'cancel' ? 'Đã rút đơn nghỉ.' : 'Đã xử lý đơn nghỉ.'
    note.value = ''
    try {
      const entries = await leave.history(requestId)
      if (current !== generation) return
      history.value = entries
      historyError.value = ''
    } catch {
      if (current !== generation) return
      historyError.value = 'Đã lưu kết quả nhưng chưa tải được lịch sử. Vui lòng tải lại.'
    }
  } catch (cause) {
    if (current !== generation) return
    error.value = leaveError(cause)
    // A timeout may mean the write committed; require fresh data before another action.
    if ([0, 409, 412, 428].includes(cause.status) || cause.status >= 500) stale.value = true
  } finally {
    saving.value = false
  }
}
watch(
  () => props.id,
  () => {
    note.value = ''
    load()
  },
  { immediate: true },
)
</script>

<template>
  <div class="leave-page">
    <RouterLink :to="isReviewer ? '/leave/inbox' : '/leave'" class="back-link"
      >← Danh sách đơn nghỉ</RouterLink
    >
    <div class="page-heading">
      <div>
        <p class="eyebrow">NGHỈ PHÉP</p>
        <h1>Chi tiết đơn nghỉ</h1>
      </div>
      <button class="button button-secondary" :disabled="busy || saving" @click="load">
        Tải lại
      </button>
    </div>
    <p v-if="busy" role="status">Đang tải đơn nghỉ…</p>
    <p v-if="error" class="alert alert-error" role="alert">{{ error }}</p>
    <p v-if="success" class="alert alert-success" role="status">{{ success }}</p>
    <section v-if="request && !busy" class="panel create-form leave-section">
      <h2>{{ leaveTypes[request.leaveType] }}</h2>
      <span class="leave-badge" :class="`leave-${request.status}`">{{
        leaveStatuses[request.status]
      }}</span>
      <dl class="leave-details">
        <div>
          <dt>Nhân viên</dt>
          <dd>{{ employeeName || (own ? auth.state.user?.email : request.employeeId) }}</dd>
        </div>
        <div>
          <dt>Thời gian nghỉ</dt>
          <dd>
            {{ dateLabel(request.startDate) }} – {{ dateLabel(request.endDate) }}
            ({{ leavePeriods[request.period || 'FULL_DAY'].toLowerCase() }})
          </dd>
        </div>
        <div>
          <dt>Lý do nghỉ</dt>
          <dd class="leave-text">{{ request.reason }}</dd>
        </div>
        <div>
          <dt>Gửi lúc</dt>
          <dd>{{ timeLabel(request.createdAt) }}</dd>
        </div>
        <div v-if="request.reviewedAt">
          <dt>Xử lý lúc</dt>
          <dd>{{ timeLabel(request.reviewedAt) }}</dd>
        </div>
        <div v-if="request.reviewNote">
          <dt>Phản hồi của HR</dt>
          <dd class="leave-text">{{ request.reviewNote }}</dd>
        </div>
      </dl>
      <p class="muted">
        Phép năm được trừ khỏi hạn mức 12 ngày khi duyệt; bảng công chưa được cập nhật tự động.
      </p>
      <template v-if="request.status === 'PENDING'">
        <p v-if="own && isReviewer" class="muted">Đơn của bạn cần HR/ADMIN khác xử lý.</p>
        <button
          v-if="own"
          class="button button-secondary"
          :disabled="saving || stale"
          @click="decide('cancel')"
        >
          {{ saving ? 'Đang xử lý…' : 'Rút đơn' }}
        </button>
        <form v-else-if="isReviewer" @submit.prevent="decide('approve')">
          <fieldset class="form-fields" :disabled="saving || stale">
            <div class="field">
              <label for="review-note">Phản hồi (bắt buộc khi từ chối)</label
              ><textarea id="review-note" v-model="note" rows="3" maxlength="2000"></textarea>
            </div>
            <div class="button-row">
              <button type="submit" class="button button-primary">Duyệt đơn</button
              ><button type="button" class="button button-secondary" @click="decide('reject')">
                Từ chối
              </button>
            </div>
          </fieldset>
        </form>
      </template>
    </section>
    <section v-if="request && !busy" class="panel create-form">
      <h2>Lịch sử xử lý</h2>
      <p v-if="historyError" class="alert alert-error" role="alert">{{ historyError }}</p>
      <ol class="leave-history">
        <li v-for="entry in history" :key="entry.id">
          <strong>{{ historyActions[entry.action] }}</strong> · {{ timeLabel(entry.occurredAt)
          }}<span class="cell-secondary">Tài khoản thực hiện: {{ entry.actorUserId }}</span>
          <p v-if="entry.note" class="leave-text">{{ entry.note }}</p>
        </li>
      </ol>
    </section>
  </div>
</template>

<style scoped src="@/assets/styles/leave.css"></style>
