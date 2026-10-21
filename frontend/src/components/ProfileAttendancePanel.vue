<script setup>
import { getDisplayLocale } from '@/i18n/index.js'

import { profileAttendance as copy } from '@/content/attendance.js'
import { common } from '@/content/common.js'

import { computed, ref } from 'vue'
import { useAsyncResource } from '@/composables/useAsyncResource.js'
import { attendance } from '@/attendance/api.js'
import { attendanceError } from '@/attendance/errors.js'
import { minutesLabel } from '@/shared/duration.js'
import { today } from '@/shared/date.js'

const month = ref(today().slice(0, 7))
const monthLabel = computed(() => month.value.split('-').reverse().join('/'))
const total = (values) =>
  values.some((value) => value != null)
    ? values.reduce((sum, value) => sum + Number(value ?? 0), 0)
    : null
const stats = computed(() => [
  [copy.actualHours, minutesLabel(total(rows.value.map((row) => row.workMinutesCounted)))],
  [
    copy.leaveDays,
    total(rows.value.map((row) => row.leaveDays))?.toLocaleString(getDisplayLocale()) ??
      common.unknown,
  ],
  [copy.overtime, minutesLabel(total(rows.value.map((row) => row.overtime?.countedMinutes)))],
])
const {
  data: rows,
  loading,
  error,
  load: retry,
} = useAsyncResource(
  () => {
    const selected = month.value
    if (!/^\d{4}-(0[1-9]|1[0-2])$/.test(selected) || selected > today().slice(0, 7)) {
      throw new Error(copy.invalidMonth)
    }
    const [year, monthNumber] = selected.split('-').map(Number)
    const end = new Date(Date.UTC(year, monthNumber, 0)).toISOString().slice(0, 10)
    return attendance.mine(`${selected}-01`, end > today() ? today() : end)
  },
  { source: month, initialValue: () => [], errorMessage: attendanceError },
)
</script>

<template>
  <section class="profile-module" :aria-label="copy.summaryLabel" :aria-busy="loading">
    <div class="profile-module-heading">
      <h3>{{ copy.monthTitle({ monthLabel: monthLabel }) }}</h3>
      <div class="field">
        <label for="profile-attendance-month">{{ copy.monthLabel }}</label>
        <input
          id="profile-attendance-month"
          v-model="month"
          type="month"
          :max="today().slice(0, 7)"
          required
        />
      </div>
    </div>
    <p v-if="loading" class="muted" role="status">{{ copy.loading }}</p>
    <div v-else-if="error" class="alert alert-error" role="alert">
      <p>{{ error }}</p>
      <button class="button button-secondary" @click="retry()">{{ common.retry }}</button>
    </div>
    <template v-else-if="rows.length">
      <dl class="profile-module-stats">
        <div v-for="[label, value] in stats" :key="label">
          <dt>{{ label }}</dt>
          <dd>{{ value }}</dd>
        </div>
      </dl>
      <p class="muted small">{{ copy.calculationHint }}</p>
    </template>
    <p v-else class="muted">{{ copy.empty }}</p>
    <RouterLink class="text-button" to="/attendance">{{ copy.detailsLink }}</RouterLink>
    <p class="small">
      <RouterLink class="text-button" to="/help/features#work-time">{{ copy.helpLink }}</RouterLink>
    </p>
  </section>
</template>
