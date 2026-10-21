<script setup>
import { getDisplayLocale } from '@/i18n/index.js'
import { COMPANY_TIMEZONE } from '@/config/presentation.js'

import { attendanceTable as copy } from '@/content/attendance.js'
import { common } from '@/content/common.js'

import { computed, ref, watch } from 'vue'
import { statuses } from '@/attendance/constants.js'
import { minutesLabel, secondsLabel } from '@/shared/duration.js'
import { timeLabel } from '@/shared/date.js'
import AppIcon from './AppIcon.vue'
import PageControls from './PageControls.vue'
const props = defineProps({
  rows: { type: Array, default: () => [] },
  management: Boolean,
  singleEmployee: Boolean,
  busy: Boolean,
  failed: Boolean,
})
defineEmits(['refresh'])
const status = ref('')
const search = ref('')
const page = ref(0)
function clearFilters() {
  status.value = ''
  search.value = ''
}
const filtered = computed(() =>
  props.rows
    .filter(
      (row) =>
        (!status.value || row.status === status.value) &&
        (!props.management ||
          `${row.employeeName} ${row.employeeCode}`
            .toLocaleLowerCase('vi')
            .includes(search.value.trim().toLocaleLowerCase('vi'))),
    )
    .toSorted((a, b) => b.workDate.localeCompare(a.workDate)),
)
const visible = computed(() => filtered.value.slice(page.value * 10, page.value * 10 + 10))
watch([status, search, () => props.rows], () => {
  page.value = 0
})
function dateLabel(value) {
  return new Intl.DateTimeFormat(getDisplayLocale(), {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  }).format(new Date(`${value}T12:00:00`))
}
</script>
<template>
  <div class="attendance-table-tools">
    <div v-if="management && !singleEmployee" class="field attendance-search">
      <label for="attendance-search">{{ copy.searchLabel }}</label>
      <input
        id="attendance-search"
        v-model="search"
        type="search"
        :placeholder="copy.searchPlaceholder"
        :disabled="busy"
      />
    </div>
    <div class="field">
      <label for="attendance-status">{{ copy.statusLabel }}</label>
      <select id="attendance-status" v-model="status" :disabled="busy">
        <option value="">{{ copy.allStatuses }}</option>
        <option v-for="(label, value) in statuses" :key="value" :value="value">{{ label }}</option>
      </select>
    </div>
    <span class="muted small">{{ copy.resultCount({ count: filtered.length }) }}</span>
  </div>
  <div
    class="table-wrap attendance-table"
    tabindex="0"
    :aria-label="copy.caption"
    :aria-busy="busy"
  >
    <table>
      <thead>
        <tr>
          <th v-if="management && !singleEmployee">{{ common.employee }}</th>
          <th>{{ common.date }}</th>
          <th>{{ copy.punches }}</th>
          <th>{{ copy.actualHours }}</th>
          <th>{{ copy.creditedHours }}</th>
          <th>{{ copy.overtime }}</th>
          <th>{{ copy.leave }}</th>
          <th>{{ copy.lateEarly }}</th>
          <th>{{ common.status }}</th>
          <th v-if="management">{{ copy.review }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in busy ? [] : visible" :key="`${row.employeeId}-${row.workDate}`">
          <td v-if="management && !singleEmployee">
            {{ row.employeeName }}<small>{{ row.employeeCode }}</small>
          </td>
          <td class="attendance-date">{{ dateLabel(row.workDate) }}</td>
          <td>
            {{ timeLabel(row.checkIn) }}<small>{{ timeLabel(row.checkOut) }}</small>
            <small v-if="row.correctionId">{{
              copy.correctedTimes({
                checkIn: timeLabel(row.originalCheckIn),
                checkOut: timeLabel(row.originalCheckOut),
              })
            }}</small>
            <RouterLink
              v-if="!management"
              :to="{ path: '/attendance/corrections', query: { date: row.workDate } }"
              class="text-button"
              >{{ copy.correctionAction }}</RouterLink
            >
          </td>
          <td>{{ secondsLabel(row.workedActualSeconds) }}</td>
          <td>{{ minutesLabel(row.workMinutesCounted) }}</td>
          <td>
            {{ minutesLabel(row.overtime?.countedMinutes ?? 0)
            }}<small>{{
              copy.approvedOvertime({ duration: minutesLabel(row.overtime?.approvedMinutes ?? 0) })
            }}</small>
          </td>
          <td>
            {{ copy.leaveDays({ leaveDays: row.leaveDays })
            }}<small v-if="row.annualLeaveMinutes">{{
              copy.annualLeave({ days: minutesLabel(row.annualLeaveMinutes) })
            }}</small
            ><small v-if="row.unpaidLeaveMinutes">{{
              copy.unpaidLeave({ days: minutesLabel(row.unpaidLeaveMinutes) })
            }}</small>
          </td>
          <td>
            {{
              copy.roundedMinutes({
                late: row.roundedLateMinutes ?? common.missingValue,
                early: row.roundedEarlyMinutes ?? common.missingValue,
              })
            }}
          </td>
          <td>
            <span class="attendance-badge" :class="`status-${row.status.toLowerCase()}`">{{
              statuses[row.status] || row.status
            }}</span
            ><small v-if="row.pendingLeaveIds?.length">{{
              copy.pendingCorrections({ count: row.pendingLeaveIds.length })
            }}</small>
            <small v-if="row.overtime?.pendingIds?.length">{{
              copy.pendingOvertime({ count: row.overtime.pendingIds.length })
            }}</small>
            <small v-if="row.permissionCoverage?.approvedRequestIds?.length">{{
              copy.approvedRequests({ count: row.permissionCoverage.approvedRequestIds.length })
            }}</small>
            <small v-if="row.permissionCoverage?.pendingRequestIds?.length">{{
              copy.pendingRequests({ count: row.permissionCoverage.pendingRequestIds.length })
            }}</small>
            <small v-if="row.permissionCoverage?.conflictingRequestIds?.length">{{
              copy.requestConflict
            }}</small>
            <small v-if="row.permissionCoverage?.unapprovedRoundedLateMinutes != null">{{
              copy.unapprovedMinutes({
                unapprovedRoundedLateMinutes: row.permissionCoverage.unapprovedRoundedLateMinutes,
                unapprovedRoundedEarlyMinutes: row.permissionCoverage.unapprovedRoundedEarlyMinutes,
              })
            }}</small>
          </td>
          <td v-if="management">
            <button
              v-if="row.recordVersion != null && row.reportState !== 'FINAL'"
              type="button"
              class="text-button"
              :disabled="busy"
              @click="$emit('refresh', row)"
            >
              {{ copy.refreshSources }}</button
            ><small v-if="row.sourceObservedAt">{{
              copy.sourcesAt({
                date: new Date(row.sourceObservedAt).toLocaleString(getDisplayLocale(), {
                  timeZone: COMPANY_TIMEZONE,
                }),
              })
            }}</small>
          </td>
        </tr>
        <tr v-if="busy || !filtered.length">
          <td :colspan="management ? (singleEmployee ? 9 : 10) : 8">
            <div class="attendance-empty">
              <AppIcon :name="busy ? 'clock' : failed ? 'info' : 'calendar'" :size="30" />
              <strong>{{ busy ? copy.loading : failed ? copy.loadFailed : copy.empty }}</strong>
              <span>{{ busy ? copy.loadingHint : failed ? copy.retryHint : copy.emptyHint }}</span>
              <button v-if="!busy && (status || search)" class="text-button" @click="clearFilters">
                {{ copy.clearFilters }}
              </button>
            </div>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
  <PageControls
    v-if="filtered.length"
    :page="page"
    :pages="Math.ceil(filtered.length / 10)"
    :total="filtered.length"
    :busy="busy"
    @change="page = $event"
  />
</template>
