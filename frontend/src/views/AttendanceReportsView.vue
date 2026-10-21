<script setup>
import { dateLabel } from '@/shared/date.js'
import { useLocalizedMessage } from '@/composables/useLocalizedMessage.js'

import { attendanceReports as copy } from '@/content/attendance.js'
import { common } from '@/content/common.js'

import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { attendance } from '@/attendance/api.js'
import { today } from '@/shared/date.js'
import { attendanceError } from '@/attendance/errors.js'
import { minutesLabel } from '@/shared/duration.js'
import { attendanceOverview, reviewReasons, summarizeAttendance } from '@/attendance/report.js'
import AttendancePeriodPanel from '@/components/AttendancePeriodPanel.vue'
import AttendanceTable from '@/components/AttendanceTable.vue'
import AttendanceReportSummary from '@/components/AttendanceReportSummary.vue'
import ReferencePicker from '@/components/ReferencePicker.vue'
import AppIcon from '@/components/AppIcon.vue'
import '@/assets/styles/attendance-report.css'

const filters = reactive({
  from: `${today().slice(0, 7)}-01`,
  until: today(),
  employeeId: '',
  departmentId: '',
})
const reportStateLabel = computed(() =>
  rows.value.length && rows.value.every((row) => row.reportState === 'FINAL')
    ? copy.closed
    : rows.value.some((row) => row.reportState === 'FINAL')
      ? copy.partiallyClosed
      : copy.provisional,
)
const rows = ref([])
const loading = ref(false)
const exporting = ref(false)
const refreshing = ref(false)
const error = useLocalizedMessage()
const actionError = useLocalizedMessage()
const notice = ref('')
const loaded = ref(null)
const selectedId = ref(null)
const onlyReviewDays = ref(false)
const summary = ref(null)
const detailHeading = ref(null)
const busy = computed(() => loading.value || exporting.value || refreshing.value)
const employees = computed(() => summarizeAttendance(rows.value))
const overview = computed(() => attendanceOverview(employees.value))
const selected = computed(() =>
  employees.value.find((person) => person.employeeId === selectedId.value),
)
const detailRows = computed(
  () =>
    selected.value?.days.filter((day) => !onlyReviewDays.value || reviewReasons(day).length) ?? [],
)
const dirty = computed(
  () => loaded.value && Object.keys(filters).some((key) => filters[key] !== loaded.value[key]),
)
const time = (value) => (value == null ? common.missingValue : minutesLabel(value))
const cards = computed(() => [
  {
    get label() {
      return copy.employeeCount
    },
    value: overview.value.employees,
    get note() {
      return copy.employeeCountHint
    },
    icon: 'user',
    tone: 'blue',
  },
  {
    get label() {
      return copy.reviewEmployeeCount
    },
    value: overview.value.reviewEmployees,
    note: copy.reviewSummary({
      reviewDays: overview.value.reviewDays,
      pendingRequests: overview.value.pendingRequests,
    }),
    icon: 'info',
    tone: 'amber',
    review: true,
  },
  {
    get label() {
      return copy.creditedHours
    },
    value: time(overview.value.workMinutes),
    note: overview.value.incompleteDays
      ? copy.incompleteHours({ incompleteDays: overview.value.incompleteDays })
      : copy.creditedHoursHint,
    icon: 'clock',
    tone: 'green',
  },
  {
    get label() {
      return copy.recordedOvertime
    },
    value: time(overview.value.otCountedMinutes),
    note: copy.approvedOvertime({ duration: time(overview.value.otApprovedMinutes) }),
    icon: 'check',
    tone: 'purple',
  },
])
async function load() {
  if (busy.value) return
  loading.value = true
  error.value = ''
  actionError.value = ''
  notice.value = ''
  rows.value = []
  loaded.value = null
  selectedId.value = null
  try {
    const request = { ...filters }
    rows.value = await attendance.report(request)
    loaded.value = request
  } catch (cause) {
    error.value = () => attendanceError(cause)
  } finally {
    loading.value = false
  }
}
async function download() {
  if (!loaded.value || busy.value) return
  exporting.value = true
  actionError.value = ''
  try {
    const blob = await attendance.export(loaded.value)
    const url = URL.createObjectURL(blob),
      anchor = document.createElement('a')
    anchor.href = url
    anchor.download = `bang-cong-${loaded.value.from}-${loaded.value.until}.csv`
    document.body.append(anchor)
    anchor.click()
    anchor.remove()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
  } catch (cause) {
    actionError.value = () => attendanceError(cause)
  } finally {
    exporting.value = false
  }
}
async function refresh(row) {
  if (busy.value) return
  refreshing.value = true
  actionError.value = ''
  notice.value = ''
  try {
    const updated = await attendance.refresh(row)
    rows.value = rows.value.map((value) =>
      value.employeeId === updated.employeeId && value.workDate === updated.workDate
        ? updated
        : value,
    )
    notice.value = copy.sourcesUpdated({ date: dateLabel(row.workDate) })
  } catch (cause) {
    actionError.value = () => attendanceError(cause)
  } finally {
    refreshing.value = false
  }
}
async function openDetails(id) {
  selectedId.value = id
  onlyReviewDays.value = false
  notice.value = ''
  actionError.value = ''
  await nextTick()
  detailHeading.value?.focus()
  detailHeading.value?.scrollIntoView({ block: 'start' })
}
async function closeDetails() {
  const id = selectedId.value
  selectedId.value = null
  notice.value = ''
  actionError.value = ''
  await nextTick()
  summary.value?.focusEmployee(id)
}
async function showReview() {
  selectedId.value = null
  await nextTick()
  summary.value?.showReview()
}
onMounted(() => {
  load()
})
</script>
<template>
  <div class="attendance-page report-page">
    <div class="page-heading report-heading">
      <div class="report-heading-copy">
        <p class="eyebrow">{{ copy.eyebrow }}</p>
        <div class="report-title-row">
          <h1>{{ copy.title }}</h1>
          <span class="report-tag">{{ reportStateLabel }}</span>
        </div>
        <p class="muted">{{ copy.description }}</p>
      </div>
      <button
        class="button button-primary"
        :disabled="busy || !loaded || !rows.length"
        @click="download"
      >
        {{ exporting ? copy.exporting : copy.exportCsv }}
      </button>
    </div>
    <p v-if="error" class="alert alert-error" role="alert">{{ error }}</p>
    <AttendancePeriodPanel :busy="busy" @updated="load" />
    <form class="panel report-filters" @submit.prevent="load">
      <fieldset :disabled="busy">
        <div class="attendance-fields">
          <div class="field">
            <label for="report-from">{{ common.fromDate }}</label
            ><input id="report-from" v-model="filters.from" type="date" :max="today()" required />
          </div>
          <div class="field">
            <label for="report-until">{{ common.toDate }}</label
            ><input
              id="report-until"
              v-model="filters.until"
              type="date"
              :min="filters.from"
              :max="today()"
              required
            />
          </div>
          <ReferencePicker
            v-model="filters.employeeId"
            resource="employees"
            :label="copy.employeeFilter"
          />
          <ReferencePicker
            v-model="filters.departmentId"
            resource="departments"
            :label="copy.departmentFilter"
          />
        </div>
        <div class="report-filter-footer">
          <button class="button button-secondary">
            {{ loading ? common.loading : copy.load }}
          </button>
        </div>
      </fieldset>
    </form>
    <div class="report-period" aria-live="polite">
      <p v-if="loaded">
        <AppIcon name="calendar" :size="17" />
        <strong>{{ dateLabel(loaded.from) }} – {{ dateLabel(loaded.until) }}</strong
        ><span>{{ copy.employeeCountSuffix({ count: employees.length }) }}</span>
      </p>
      <p v-else>{{ loading ? copy.loading : copy.empty }}</p>
      <span v-if="dirty" class="report-warning">{{ copy.filtersChanged }}</span>
    </div>
    <div class="attendance-stats" :aria-label="copy.summaryLabel" :aria-busy="loading">
      <article
        v-for="card in cards"
        :key="card.label"
        class="panel attendance-stat report-stat"
        :class="{ 'report-stat-attention': card.review && overview.reviewEmployees }"
      >
        <span class="attendance-stat-icon" :class="card.tone">
          <AppIcon :name="card.icon" />
        </span>
        <p>{{ card.label }}</p>
        <strong>{{ loading || !loaded ? common.missingValue : card.value }}</strong>
        <small>{{ loading || !loaded ? copy.pendingData : card.note }}</small>
        <button
          v-if="card.review"
          class="text-button"
          :disabled="busy || !overview.reviewEmployees"
          @click="showReview"
        >
          {{ copy.showReview }} <AppIcon name="arrow" :size="14" />
        </button>
      </article>
    </div>
    <div class="report-explanation">
      <AppIcon name="info" :size="18" />
      <p>
        {{ copy.calculationHint }}
        <RouterLink class="text-button" to="/help/features#work-time">{{
          copy.formulaLink
        }}</RouterLink>
      </p>
    </div>
    <p v-if="actionError" class="alert alert-error" role="alert">{{ actionError }}</p>
    <p v-if="notice" class="alert alert-success" role="status">{{ notice }}</p>
    <AttendanceReportSummary
      v-show="!selected"
      ref="summary"
      :employees="employees"
      :busy="loading"
      :failed="!!error"
      @select="openDetails"
      @retry="load"
    />
    <section
      v-if="selected"
      class="panel report-detail"
      aria-labelledby="report-detail-title"
      :aria-busy="refreshing"
    >
      <button class="text-button report-back" :disabled="refreshing" @click="closeDetails">
        {{ copy.backToSummary }}
      </button>
      <div class="attendance-section-heading">
        <div>
          <p class="eyebrow">{{ copy.detailEyebrow({ employeeCode: selected.employeeCode }) }}</p>
          <h2 id="report-detail-title" ref="detailHeading" tabindex="-1">
            {{ selected.employeeName }}
          </h2>
          <p class="muted">
            {{
              copy.detailPeriod({
                start: dateLabel(loaded.from),
                end: dateLabel(loaded.until),
                days: selected.days.length,
              })
            }}
          </p>
        </div>
        <span class="report-tag" :class="{ 'report-tag-warning': selected.reviewDays }">{{
          copy.reviewDays({ reviewDays: selected.reviewDays })
        }}</span>
      </div>
      <div class="report-detail-metrics">
        <div>
          <span>{{ copy.creditedHoursLabel }}</span
          ><strong>{{ time(selected.workMinutes) }}</strong
          ><small v-if="selected.incompleteDays" class="report-warning">{{
            copy.incompleteDays({ incompleteDays: selected.incompleteDays })
          }}</small>
        </div>
        <div>
          <span>{{ copy.leaveDays }}</span
          ><strong>{{ time(selected.annualMinutes) }} / {{ time(selected.unpaidMinutes) }}</strong>
        </div>
        <div>
          <span>{{ copy.lateEarly }}</span
          ><strong
            >{{ selected.lateMinutes == null ? common.missingValue : `${selected.lateMinutes}p` }} /
            {{
              selected.earlyMinutes == null ? common.missingValue : `${selected.earlyMinutes}p`
            }}</strong
          ><small>{{ copy.lateEarlyDays({ lateEarlyDays: selected.lateEarlyDays }) }}</small>
        </div>
        <div>
          <span>{{ copy.overtime }}</span
          ><strong
            >{{ time(selected.otCountedMinutes) }} / {{ time(selected.otApprovedMinutes) }}</strong
          >
        </div>
      </div>
      <p class="muted small">
        {{
          copy.ongoingSummary({
            ongoingDays: selected.ongoingDays,
            noScheduleDays: selected.noScheduleDays,
            pendingRequests: selected.pendingRequests,
          })
        }}
      </p>
      <label class="report-review-toggle"
        ><input v-model="onlyReviewDays" type="checkbox" :disabled="refreshing" />{{
          copy.reviewOnly
        }}</label
      >
      <AttendanceTable
        :key="selected.employeeId"
        :rows="detailRows"
        management
        single-employee
        :busy="refreshing"
        @refresh="refresh"
      />
    </section>
  </div>
</template>
