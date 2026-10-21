<script setup>
import { common } from '@/content/common.js'

import { attendanceStats as copy } from '@/content/attendance.js'

import { computed } from 'vue'
import AppIcon from './AppIcon.vue'
import { minutesLabel } from '@/shared/duration.js'

const props = defineProps({ rows: { type: Array, default: () => [] }, busy: Boolean })
const stats = computed(() => [
  {
    get label() {
      return copy.completedDays
    },
    value: props.rows.filter((r) => r.status === 'CLOSED').length,
    get unit() {
      return copy.workdayUnit
    },
    icon: 'check',
    tone: 'green',
  },
  {
    get label() {
      return copy.creditedHours
    },
    value: props.rows.some((r) => r.workMinutesCounted != null)
      ? minutesLabel(props.rows.reduce((sum, r) => sum + (r.workMinutesCounted || 0), 0))
      : common.missingValue,
    get unit() {
      return copy.currentPeriod
    },
    icon: 'clock',
    tone: 'blue',
  },
  {
    get label() {
      return copy.lateEarly
    },
    value: props.rows.filter((r) => r.roundedLateMinutes > 0 || r.roundedEarlyMinutes > 0).length,
    get unit() {
      return copy.attentionDayUnit
    },
    icon: 'info',
    tone: 'amber',
  },
  {
    get label() {
      return copy.leaveDays
    },
    value: props.rows.reduce((sum, r) => sum + (r.leaveDays || 0), 0),
    get unit() {
      return copy.periodDayUnit
    },
    icon: 'calendar',
    tone: 'purple',
  },
])
</script>

<template>
  <div class="attendance-stats" :aria-label="copy.summaryLabel" :aria-busy="busy">
    <article v-for="stat in stats" :key="stat.label" class="panel attendance-stat">
      <span class="attendance-stat-icon" :class="stat.tone"><AppIcon :name="stat.icon" /></span>
      <p>{{ stat.label }}</p>
      <strong>{{ busy ? common.missingValue : stat.value }}</strong>
      <small>{{ stat.unit }}</small>
    </article>
  </div>
</template>
