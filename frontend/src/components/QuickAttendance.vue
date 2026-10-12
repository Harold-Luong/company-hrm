<script setup>
import { computed, onMounted, ref } from 'vue'
import { auth } from '@/auth/session.js'
import { currentAttendance, recordAttendance } from '@/attendance/current.js'
import { attendanceError, shiftLabel, statuses, timeLabel, today } from '@/attendance/helpers.js'
import AppIcon from './AppIcon.vue'

const current = ref(null)
const busy = ref(true)
const error = ref('')
const message = ref('')
const linked = computed(() => !!auth.state.user?.employeeId)
const canEnter = computed(
  () => current.value?.row?.status === 'NOT_STARTED' && !!current.value?.schedule?.definition,
)
const canLeave = computed(() => current.value?.row?.status === 'OPEN')
const status = computed(() => {
  if (!linked.value) return 'Chưa liên kết hồ sơ'
  if (busy.value) return 'Đang cập nhật…'
  if (error.value && !current.value) return 'Chưa tải được dữ liệu'
  return statuses[current.value?.row?.status] || 'Chưa có dữ liệu'
})

async function load() {
  current.value = null
  if (linked.value) current.value = await currentAttendance()
}
async function refresh() {
  if (busy.value) return
  busy.value = true
  error.value = ''
  message.value = ''
  try {
    await load()
  } catch (cause) {
    error.value = attendanceError(cause)
  } finally {
    busy.value = false
  }
}
async function punch(action) {
  if (busy.value || !linked.value || !(action === 'check-in' ? canEnter.value : canLeave.value))
    return
  busy.value = true
  error.value = ''
  message.value = ''
  try {
    await recordAttendance(action, current.value.currentDate)
    message.value = action === 'check-in' ? 'Đã ghi nhận giờ vào.' : 'Đã ghi nhận giờ ra.'
    await load()
  } catch (cause) {
    error.value = attendanceError(cause)
  } finally {
    busy.value = false
  }
}
onMounted(() => {
  busy.value = false
  refresh()
})
</script>

<template>
  <section
    class="panel quick-attendance"
    aria-labelledby="quick-attendance-title"
    :aria-busy="busy"
  >
    <div class="quick-attendance-heading">
      <div class="quick-attendance-title">
        <span class="metric-icon green"><AppIcon name="clock" :size="24" /></span>
        <div>
          <p class="eyebrow">BẮT ĐẦU NGÀY LÀM VIỆC</p>
          <h2 id="quick-attendance-title">Chấm công nhanh</h2>
        </div>
      </div>
      <button class="button button-secondary" :disabled="busy || !linked" @click="refresh">
        Tải lại
      </button>
    </div>
    <p v-if="!linked" class="muted">Liên hệ HR để liên kết hồ sơ nhân viên trước khi chấm công.</p>
    <p v-else-if="current?.schedule?.definition" class="muted">
      {{ current.schedule.definition.name }} · {{ shiftLabel(current.schedule.definition) }}
      <span v-if="current.currentDate !== today()">
        · Ngày công {{ current.currentDate.split('-').reverse().join('/') }}
      </span>
    </p>
    <p v-else-if="!busy && !error" class="muted">
      Chưa có lịch làm việc. Liên hệ HR để được phân ca.
    </p>
    <div class="quick-attendance-body">
      <dl class="quick-attendance-status">
        <div>
          <dt>Trạng thái</dt>
          <dd>{{ status }}</dd>
        </div>
        <div>
          <dt>Giờ vào</dt>
          <dd>{{ timeLabel(current?.row?.checkIn) }}</dd>
        </div>
        <div>
          <dt>Giờ ra</dt>
          <dd>{{ timeLabel(current?.row?.checkOut) }}</dd>
        </div>
      </dl>
      <div class="button-row quick-attendance-actions">
        <button
          class="button button-primary"
          :disabled="busy || !linked || !canEnter"
          @click="punch('check-in')"
        >
          <AppIcon name="arrow" :size="18" />Ghi nhận giờ vào
        </button>
        <button
          :class="['button', canLeave ? 'button-primary' : 'button-secondary']"
          :disabled="busy || !linked || !canLeave"
          @click="punch('check-out')"
        >
          <AppIcon name="logout" :size="18" />Ghi nhận giờ ra
        </button>
      </div>
    </div>
    <p v-if="error" class="alert alert-error" role="alert">{{ error }}</p>
    <p v-if="message" class="info-strip" role="status">{{ message }}</p>
    <div class="quick-attendance-footer">
      <span class="muted small">Chấm công qua mạng công ty · Giờ Việt Nam</span>
      <RouterLink to="/attendance" class="text-button"
        >Xem chi tiết chấm công <AppIcon name="arrow" :size="16"
      /></RouterLink>
    </div>
  </section>
</template>

<style scoped>
.quick-attendance {
  padding: 26px;
  margin-bottom: 25px;
  border-color: #cce5dc;
  background: linear-gradient(115deg, #f0f8f4, #fff 65%);
}
.quick-attendance-heading,
.quick-attendance-title,
.quick-attendance-body,
.quick-attendance-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  flex-wrap: wrap;
}
.quick-attendance-title {
  justify-content: flex-start;
  gap: 12px;
}
.quick-attendance-title .metric-icon {
  margin: 0;
  width: 44px;
  height: 44px;
}
.quick-attendance-title .eyebrow {
  margin: 0 0 5px;
}
.quick-attendance-title h2 {
  margin: 0;
  font-size: 21px;
}
.quick-attendance > .muted {
  margin: 16px 0 0;
  font-size: 12px;
}
.quick-attendance-body {
  padding: 22px 0;
}
.quick-attendance-status {
  display: flex;
  flex-wrap: wrap;
  gap: 20px 36px;
  margin: 0;
}
.quick-attendance-status dt {
  font-size: 12px;
  color: var(--muted);
}
.quick-attendance-status dd {
  margin: 7px 0 0;
  font-size: 17px;
  font-weight: 650;
}
.quick-attendance-footer {
  border-top: 1px solid var(--border);
  padding-top: 16px;
}
.quick-attendance-footer a {
  display: inline-flex;
  gap: 8px;
  align-items: center;
}
@media (max-width: 640px) {
  .quick-attendance {
    padding: 18px;
  }
  .quick-attendance-heading {
    align-items: flex-start;
    gap: 12px;
  }
  .quick-attendance-status {
    width: 100%;
    gap: 18px;
  }
  .quick-attendance-status > div:first-child {
    width: 100%;
  }
  .quick-attendance-actions {
    width: 100%;
  }
  .quick-attendance-actions .button {
    flex: 1 1 140px;
  }
}
</style>
