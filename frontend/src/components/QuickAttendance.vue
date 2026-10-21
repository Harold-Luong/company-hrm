<script setup>
import { dateLabel } from '@/shared/date.js'
import { useLocalizedMessage } from '@/composables/useLocalizedMessage.js'

import { quickAttendance as copy } from '@/content/attendance.js'
import { common } from '@/content/common.js'

import { computed, onMounted, ref } from 'vue'
import { auth } from '@/auth/session.js'
import { currentAttendance, recordAttendance } from '@/attendance/current.js'
import { attendanceError } from '@/attendance/errors.js'
import { shiftLabel } from '@/attendance/shifts.js'
import { statuses } from '@/attendance/constants.js'
import { timeLabel, today } from '@/shared/date.js'
import AppIcon from './AppIcon.vue'

const current = ref(null)
const busy = ref(true)
const error = useLocalizedMessage()
const message = useLocalizedMessage()
const linked = computed(() => !!auth.state.user?.employeeId)
const canEnter = computed(
  () => current.value?.row?.status === 'NOT_STARTED' && !!current.value?.schedule?.definition,
)
const canLeave = computed(() => current.value?.row?.status === 'OPEN')
const status = computed(() => {
  if (!linked.value) return copy.unlinkedStatus
  if (busy.value) return copy.updating
  if (error.value && !current.value) return copy.failed
  return statuses[current.value?.row?.status] || copy.empty
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
    error.value = () => attendanceError(cause)
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
    message.value = () => (action === 'check-in' ? copy.checkedIn : copy.checkedOut)
    await load()
  } catch (cause) {
    error.value = () => attendanceError(cause)
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
          <p class="eyebrow">{{ copy.eyebrow }}</p>
          <h2 id="quick-attendance-title">{{ copy.title }}</h2>
        </div>
      </div>
      <button class="button button-secondary" :disabled="busy || !linked" @click="refresh">
        {{ common.reload }}
      </button>
    </div>
    <p v-if="!linked" class="muted">{{ copy.unlinkedHint }}</p>
    <p v-else-if="current?.schedule?.definition" class="muted">
      {{ current.schedule.definition.name }} · {{ shiftLabel(current.schedule.definition) }}
      <span v-if="current.currentDate !== today()">
        {{ copy.workDate({ date: dateLabel(current.currentDate) }) }}
      </span>
    </p>
    <p v-else-if="!busy && !error" class="muted">{{ copy.noSchedule }}</p>
    <div class="quick-attendance-body">
      <dl class="quick-attendance-status">
        <div>
          <dt>{{ common.status }}</dt>
          <dd>{{ status }}</dd>
        </div>
        <div>
          <dt>{{ copy.checkInTime }}</dt>
          <dd>{{ timeLabel(current?.row?.checkIn) }}</dd>
        </div>
        <div>
          <dt>{{ copy.checkOutTime }}</dt>
          <dd>{{ timeLabel(current?.row?.checkOut) }}</dd>
        </div>
      </dl>
      <div class="button-row quick-attendance-actions">
        <button
          class="button button-primary"
          :disabled="busy || !linked || !canEnter"
          @click="punch('check-in')"
        >
          <AppIcon name="arrow" :size="18" />{{ copy.checkIn }}
        </button>
        <button
          :class="['button', canLeave ? 'button-primary' : 'button-secondary']"
          :disabled="busy || !linked || !canLeave"
          @click="punch('check-out')"
        >
          <AppIcon name="logout" :size="18" />{{ copy.checkOut }}
        </button>
      </div>
    </div>
    <p v-if="error" class="alert alert-error" role="alert">{{ error }}</p>
    <p v-if="message" class="info-strip" role="status">{{ message }}</p>
    <div class="quick-attendance-footer">
      <span class="muted small">{{ copy.timezoneHint }}</span>
      <RouterLink to="/attendance" class="text-button"
        >{{ copy.details }} <AppIcon name="arrow" :size="16"
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
