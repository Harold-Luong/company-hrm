<script setup>
import { useLocalizedMessage } from '@/composables/useLocalizedMessage.js'

import { getDisplayLocale } from '@/i18n/index.js'
import { COMPANY_TIMEZONE } from '@/config/presentation.js'

import { attendance as copy } from '@/content/attendance.js'
import { common } from '@/content/common.js'

import { computed, onMounted, ref } from 'vue'
import { attendance } from '@/attendance/api.js'
import { currentAttendance, recordAttendance } from '@/attendance/current.js'
import { today, timeLabel } from '@/shared/date.js'
import { attendanceError } from '@/attendance/errors.js'
import { statuses } from '@/attendance/constants.js'
import { shiftLabel } from '@/attendance/shifts.js'
import { minutesLabel } from '@/shared/duration.js'
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
  error = useLocalizedMessage(),
  message = useLocalizedMessage()
const canEnter = computed(() => row.value?.status === 'NOT_STARTED' && schedule.value?.definition)
const canLeave = computed(() => row.value?.status === 'OPEN')
const dateTitle = computed(() =>
  new Intl.DateTimeFormat(getDisplayLocale(), {
    weekday: 'long',
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    timeZone: COMPANY_TIMEZONE,
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
    const [current, history] = await Promise.all([
      currentAttendance(),
      attendance.mine(from.value, until.value),
    ])
    currentDate.value = current.currentDate
    row.value = current.row
    schedule.value = current.schedule
    rows.value = history
  } catch (cause) {
    error.value = () => attendanceError(cause)
  } finally {
    busy.value = false
  }
}
async function punch(action) {
  if (busy.value) return
  busy.value = true
  error.value = ''
  message.value = ''
  try {
    await recordAttendance(action, currentDate.value)
    message.value = () => (action === 'check-in' ? copy.checkedIn : copy.checkedOut)
    await load()
  } catch (cause) {
    error.value = () => attendanceError(cause)
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
        <p class="eyebrow">{{ copy.eyebrow }}</p>
        <h1>{{ copy.title }}</h1>
        <p class="muted">{{ copy.description }}</p>
      </div>
      <button class="button button-secondary" :disabled="busy" @click="load">
        {{ common.reload }}
      </button>
    </div>
    <p v-if="error" class="alert alert-error" role="alert">{{ error }}</p>
    <p v-if="message" class="info-strip" role="status">{{ message }}</p>
    <div class="attendance-today-layout">
      <section class="panel create-form attendance-today" :aria-busy="busy">
        <div class="attendance-section-heading">
          <div>
            <p class="eyebrow">{{ copy.todayLabel }}</p>
            <h2>{{ dateTitle }}</h2>
          </div>
          <span class="attendance-stat-icon green">
            <AppIcon name="clock" :size="24" />
          </span>
        </div>
        <p class="muted">
          <template v-if="schedule?.definition">
            {{ schedule.definition.name }} · {{ shiftLabel(schedule.definition) }}
          </template>
          <template v-else-if="busy">{{ copy.loadingSchedule }}</template>
          <template v-else>{{ copy.noSchedule }}</template>
        </p>
        <div class="attendance-summary">
          <div>
            {{ common.status
            }}<strong>{{ busy ? common.loading : statuses[row?.status] || copy.empty }}</strong>
          </div>
          <div>
            {{ copy.checkInTime }}<strong>{{ timeLabel(row?.checkIn) }}</strong>
          </div>
          <div>
            {{ copy.checkOutTime }}<strong>{{ timeLabel(row?.checkOut) }}</strong>
          </div>
          <div>
            {{ copy.creditedHours }}<strong>{{ minutesLabel(row?.workMinutesCounted) }}</strong>
          </div>
        </div>
        <div class="button-row">
          <button
            class="button button-primary"
            :disabled="busy || !canEnter"
            @click="punch('check-in')"
          >
            <AppIcon name="arrow" :size="18" />{{ copy.checkIn }}</button
          ><button
            class="button button-secondary"
            :disabled="busy || !canLeave"
            @click="punch('check-out')"
          >
            <AppIcon name="logout" :size="18" />{{ copy.checkOut }}
          </button>
        </div>
        <p class="muted small attendance-note">{{ copy.calculationHint }}</p>
        <p v-if="schedule?.definition" class="muted small">
          {{
            copy.recordingWindow({
              start: schedule.definition.checkInFrom.slice(0, 5),
              end: schedule.definition.checkOutUntil.slice(0, 5),
            })
          }}
        </p>
      </section>
      <aside class="panel create-form attendance-guide">
        <h2>{{ copy.scheduleTitle }}</h2>
        <template v-if="schedule?.definition">
          <div
            v-for="interval in schedule.definition.intervals"
            :key="interval.period"
            class="attendance-shift-period"
          >
            <span class="attendance-timeline-dot"></span>
            <div>
              <strong>{{ interval.period === 'MORNING' ? copy.morning : copy.afternoon }}</strong>
              <p>{{ interval.start.slice(0, 5) }} — {{ interval.end.slice(0, 5) }}</p>
            </div>
          </div>
          <div class="attendance-guide-note">
            <AppIcon name="info" :size="18" /><span>{{ copy.breaksHint }}</span>
          </div>
        </template>
        <p v-else class="muted">
          {{ busy ? copy.loadingSchedule : error ? copy.scheduleFailed : copy.scheduleMissing }}
        </p>
      </aside>
    </div>
    <AttendanceStats :rows="rows" :busy="busy" />
    <section class="panel create-form">
      <div class="attendance-section-heading">
        <div>
          <h2>{{ copy.historyTitle }}</h2>
          <p class="muted small">{{ copy.historyDescription }}</p>
        </div>
        <span class="attendance-badge">{{ copy.provisional }}</span>
      </div>
      <form class="attendance-fields" @submit.prevent="load">
        <div class="field">
          <label for="mine-from">{{ common.fromDate }}</label
          ><input id="mine-from" v-model="from" type="date" :max="today()" required />
        </div>
        <div class="field">
          <label for="mine-until">{{ common.toDate }}</label
          ><input id="mine-until" v-model="until" type="date" :min="from" :max="today()" required />
        </div>
        <div class="field">
          <button class="button button-secondary" :disabled="busy">{{ copy.loadHistory }}</button>
        </div>
      </form>
      <p class="muted">{{ copy.reconciliationHint }}</p>
      <AttendanceTable :rows="rows" :busy="busy" :failed="!!error" />
    </section>
  </div>
</template>
