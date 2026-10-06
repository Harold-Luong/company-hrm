<script setup>
import { computed } from 'vue'
import AppIcon from './AppIcon.vue'
import { minutesLabel } from '@/attendance/helpers.js'

const props = defineProps({ rows: { type: Array, default: () => [] }, busy: Boolean })
const stats = computed(() => [
  {
    label: 'Ngày đã hoàn tất',
    value: props.rows.filter((r) => r.status === 'CLOSED').length,
    unit: 'ngày công',
    icon: 'check',
    tone: 'green',
  },
  {
    label: 'Tổng giờ tính công',
    value: props.rows.some((r) => r.workMinutesCounted != null)
      ? minutesLabel(props.rows.reduce((sum, r) => sum + (r.workMinutesCounted || 0), 0))
      : '—',
    unit: 'trong kỳ đang xem',
    icon: 'clock',
    tone: 'blue',
  },
  {
    label: 'Đi trễ / về sớm',
    value: props.rows.filter((r) => r.roundedLateMinutes > 0 || r.roundedEarlyMinutes > 0).length,
    unit: 'ngày cần lưu ý',
    icon: 'info',
    tone: 'amber',
  },
  {
    label: 'Ngày nghỉ phép',
    value: props.rows.reduce((sum, r) => sum + (r.leaveDays || 0), 0),
    unit: 'ngày trong kỳ',
    icon: 'calendar',
    tone: 'purple',
  },
])
</script>

<template>
  <div class="attendance-stats" aria-label="Tổng hợp kỳ đang xem" :aria-busy="busy">
    <article v-for="stat in stats" :key="stat.label" class="panel attendance-stat">
      <span class="attendance-stat-icon" :class="stat.tone"><AppIcon :name="stat.icon" /></span>
      <p>{{ stat.label }}</p>
      <strong>{{ busy ? '—' : stat.value }}</strong>
      <small>{{ stat.unit }}</small>
    </article>
  </div>
</template>
