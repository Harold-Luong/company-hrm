<script setup>
import { computed, onMounted, ref } from 'vue'
import { auth } from '@/auth/session.js'
import { attendance } from '@/attendance/api.js'
import {
  today,
  attendanceError,
  statuses,
  shiftLabel,
  timeLabel,
  minutesLabel,
} from '@/attendance/helpers.js'
import AttendanceNav from '@/components/AttendanceNav.vue'
import AttendanceTable from '@/components/AttendanceTable.vue'
import AttendanceStats from '@/components/AttendanceStats.vue'
import AppIcon from '@/components/AppIcon.vue'
const currentDate = ref(today())
const from = ref(`${today().slice(0, 7)}-01`),
  until = ref(today())
const row = ref(null),
  schedule = ref(null),
  rows = ref([])
const busy = ref(false),
  error = ref(''),
  message = ref('')
const canEnter = computed(() => row.value?.status === 'NOT_STARTED' && schedule.value?.definition)
const canLeave = computed(() => row.value?.status === 'OPEN')
const dateTitle = computed(() =>
  new Intl.DateTimeFormat('vi-VN', {
    weekday: 'long',
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    timeZone: 'Asia/Ho_Chi_Minh',
  }).format(new Date(`${currentDate.value}T12:00:00+07:00`)),
)
async function load() {
  busy.value = true
  error.value = ''
  row.value = null
  schedule.value = null
  rows.value = []
  currentDate.value = today()
  try {
    const [day, plan, history] = await Promise.all([
      attendance.mine(currentDate.value, currentDate.value),
      attendance.schedule(currentDate.value, currentDate.value),
      attendance.mine(from.value, until.value),
    ])
    row.value = day[0] || null
    schedule.value = plan[0] || null
    rows.value = history
  } catch (cause) {
    error.value = attendanceError(cause)
  } finally {
    busy.value = false
  }
}
async function punch(action) {
  if (busy.value) return
  busy.value = true
  error.value = ''
  message.value = ''
  const storage = `attendance:${auth.state.user?.id}:${today()}:${action}`
  try {
    let key = sessionStorage.getItem(storage)
    if (!key) {
      key = crypto.randomUUID()
      sessionStorage.setItem(storage, key)
    }
    await attendance.punch(action, key)
    message.value = action === 'check-in' ? 'Đã ghi nhận giờ vào.' : 'Đã ghi nhận giờ ra.'
    await load()
  } catch (cause) {
    error.value = attendanceError(cause)
  } finally {
    busy.value = false
  }
}
onMounted(load)
</script>
<template>
  <div class="attendance-page">
    <div class="page-heading">
      <div>
        <p class="eyebrow">CHẤM CÔNG</p>
        <h1>Công của tôi</h1>
        <p class="muted">Xem lịch làm việc và ghi nhận giờ vào, giờ ra qua mạng công ty.</p>
      </div>
      <button class="button button-secondary" :disabled="busy" @click="load">Tải lại</button>
    </div>
    <AttendanceNav />
    <p v-if="error" class="alert alert-error" role="alert">{{ error }}</p>
    <p v-if="message" class="info-strip" role="status">{{ message }}</p>
    <div class="attendance-today-layout">
      <section class="panel create-form attendance-today" :aria-busy="busy">
        <div class="attendance-section-heading">
          <div>
            <p class="eyebrow">CHẤM CÔNG HÔM NAY</p>
            <h2>{{ dateTitle }}</h2>
          </div>
          <span class="attendance-stat-icon green"><AppIcon name="clock" :size="24" /></span>
        </div>
        <p class="muted">
          {{ schedule?.definition?.name || 'Liên hệ HR nếu chưa được phân ca.' }} ·
          {{ shiftLabel(schedule?.definition) }}
        </p>
        <div class="attendance-summary">
          <div>
            Trạng thái<strong>{{
              busy ? 'Đang tải…' : statuses[row?.status] || 'Chưa có dữ liệu'
            }}</strong>
          </div>
          <div>
            Giờ vào<strong>{{ timeLabel(row?.checkIn) }}</strong>
          </div>
          <div>
            Giờ ra<strong>{{ timeLabel(row?.checkOut) }}</strong>
          </div>
          <div>
            Giờ tính công<strong>{{ minutesLabel(row?.workMinutesCounted) }}</strong>
          </div>
        </div>
        <div class="button-row">
          <button
            class="button button-primary"
            :disabled="busy || !canEnter"
            @click="punch('check-in')"
          >
            <AppIcon name="arrow" :size="18" />Ghi nhận giờ vào</button
          ><button
            class="button button-secondary"
            :disabled="busy || !canLeave"
            @click="punch('check-out')"
          >
            <AppIcon name="logout" :size="18" />Ghi nhận giờ ra
          </button>
        </div>
        <p class="muted small attendance-note">
          Giờ ghi nhận lấy từ hệ thống. Đi trễ/về sớm làm tròn lên 15 phút; thời gian thực tế được
          giữ riêng.
        </p>
        <p v-if="schedule?.definition" class="muted small">
          Được chấm công từ {{ schedule.definition.checkInFrom.slice(0, 5) }} đến
          {{ schedule.definition.checkOutUntil.slice(0, 5) }} · giờ Việt Nam.
        </p>
      </section>
      <aside class="panel create-form attendance-guide">
        <h2>Lịch làm việc hôm nay</h2>
        <template v-if="schedule?.definition">
          <div
            v-for="interval in schedule.definition.intervals"
            :key="interval.period"
            class="attendance-shift-period"
          >
            <span class="attendance-timeline-dot"></span>
            <div>
              <strong>{{ interval.period === 'MORNING' ? 'Buổi sáng' : 'Buổi chiều' }}</strong>
              <p>{{ interval.start.slice(0, 5) }} — {{ interval.end.slice(0, 5) }}</p>
            </div>
          </div>
          <div class="attendance-guide-note">
            <AppIcon name="info" :size="18" /><span
              >Khoảng nghỉ giữa các buổi không tính vào giờ làm việc.</span
            >
          </div>
        </template>
        <p v-else class="muted">
          {{
            busy
              ? 'Đang tải lịch làm việc…'
              : error
                ? 'Chưa tải được lịch làm việc. Vui lòng thử lại.'
                : 'Chưa có lịch làm việc. Liên hệ HR để được phân ca.'
          }}
        </p>
        <RouterLink to="/attendance/requests" class="text-button attendance-related-link"
          >Xin đi trễ / về sớm <AppIcon name="arrow" :size="16"
        /></RouterLink>
        <RouterLink to="/leave" class="text-button attendance-related-link"
          >Đến nghỉ phép của tôi <AppIcon name="arrow" :size="16"
        /></RouterLink>
      </aside>
    </div>
    <AttendanceStats :rows="rows" :busy="busy" />
    <section class="panel create-form">
      <div class="attendance-section-heading">
        <div>
          <h2>Lịch sử công</h2>
          <p class="muted small">Theo dõi giờ làm và trạng thái chấm công trong kỳ.</p>
        </div>
        <span class="attendance-badge">Tạm tính</span>
      </div>
      <form class="attendance-fields" @submit.prevent="load">
        <div class="field">
          <label for="mine-from">Từ ngày</label
          ><input id="mine-from" v-model="from" type="date" :max="today()" required />
        </div>
        <div class="field">
          <label for="mine-until">Đến ngày</label
          ><input id="mine-until" v-model="until" type="date" :min="from" :max="today()" required />
        </div>
        <div class="field">
          <button class="button button-secondary" :disabled="busy">Xem lịch sử</button>
        </div>
      </form>
      <p class="muted">
        Bảng công tạm tính. Đơn phép duyệt sau lúc chấm công cần HR cập nhật để đối soát.
      </p>
      <AttendanceTable :rows="rows" :busy="busy" :failed="!!error" />
    </section>
  </div>
</template>
