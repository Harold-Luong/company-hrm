<script setup>
import { getDisplayLocale } from '@/i18n/index.js'
import { attendanceReportSummary as copy } from '@/content/attendance.js'
import { common } from '@/content/common.js'

import { computed, ref, watch } from 'vue'
import { minutesLabel } from '@/shared/duration.js'
import AppIcon from './AppIcon.vue'
import PageControls from './PageControls.vue'
const props = defineProps({
  employees: { type: Array, required: true },
  busy: Boolean,
  failed: Boolean,
})
const emit = defineEmits(['select', 'retry'])
const search = ref('')
const scope = ref('all')
const sort = ref('review')
const page = ref(0)
const heading = ref(null)
const employeeButtons = new Map()
const filtered = computed(() =>
  props.employees
    .filter((person) => {
      const matches = `${person.employeeCode} ${person.employeeName}`
        .toLocaleLowerCase('vi')
        .includes(search.value.trim().toLocaleLowerCase('vi'))
      return (
        matches &&
        (scope.value === 'all' ||
          (scope.value === 'review' && person.reviewDays > 0) ||
          (scope.value === 'incomplete' && person.incompleteDays > 0) ||
          (scope.value === 'late' && person.lateEarlyDays > 0))
      )
    })
    .toSorted(
      (a, b) =>
        (sort.value === 'review' ? b.reviewDays - a.reviewDays : 0) ||
        a.employeeName.localeCompare(b.employeeName, getDisplayLocale()) ||
        a.employeeCode.localeCompare(b.employeeCode, getDisplayLocale()),
    ),
)
const visible = computed(() => filtered.value.slice(page.value * 10, page.value * 10 + 10))
watch([search, scope, sort], () => {
  page.value = 0
})
watch(
  () => props.employees,
  () => {
    page.value = Math.min(page.value, Math.max(0, Math.ceil(filtered.value.length / 10) - 1))
  },
)
function clearFilters() {
  search.value = ''
  scope.value = 'all'
}
function showReview() {
  scope.value = 'review'
  heading.value?.focus()
}
function focusEmployee(id) {
  ;(employeeButtons.get(id) ?? heading.value)?.focus()
}
defineExpose({ showReview, focusEmployee })
</script>
<template>
  <section class="panel report-summary" :aria-busy="busy" aria-labelledby="report-summary-title">
    <div class="attendance-section-heading">
      <div>
        <h2 id="report-summary-title" ref="heading" tabindex="-1">{{ copy.title }}</h2>
        <p class="muted">{{ copy.description }}</p>
      </div>
      <span class="report-tag">{{ copy.employeeCount({ count: employees.length }) }}</span>
    </div>
    <div class="report-toolbar">
      <div class="field">
        <label for="report-search">{{ copy.searchLabel }}</label>
        <input
          id="report-search"
          v-model="search"
          type="search"
          :placeholder="copy.searchPlaceholder"
          :disabled="busy"
        />
      </div>
      <div class="field">
        <label for="report-scope">{{ copy.filterLabel }}</label>
        <select id="report-scope" v-model="scope" :disabled="busy">
          <option value="all">{{ copy.allEmployees }}</option>
          <option value="review">{{ copy.needsReview }}</option>
          <option value="incomplete">{{ copy.incomplete }}</option>
          <option value="late">{{ copy.lateOrEarly }}</option>
        </select>
      </div>
      <div class="field">
        <label for="report-sort">{{ copy.sortLabel }}</label>
        <select id="report-sort" v-model="sort" :disabled="busy">
          <option value="review">{{ copy.sortByReview }}</option>
          <option value="name">{{ copy.sortByName }}</option>
        </select>
      </div>
    </div>
    <div class="report-table-wrap" tabindex="0" :aria-label="copy.tableLabel">
      <table class="report-table">
        <thead>
          <tr>
            <th scope="col">{{ common.employee }}</th>
            <th scope="col">{{ copy.creditedHours }}</th>
            <th scope="col">{{ copy.leaveDays }}</th>
            <th scope="col" :title="copy.roundedMinutesHint">{{ copy.lateEarly }}</th>
            <th scope="col">{{ copy.overtime }}</th>
            <th scope="col">{{ copy.review }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="person in busy ? [] : visible" :key="person.employeeId">
            <td class="report-person" :data-label="common.employee">
              <button
                :ref="
                  (el) =>
                    el
                      ? employeeButtons.set(person.employeeId, el)
                      : employeeButtons.delete(person.employeeId)
                "
                class="text-button report-detail-button"
                :aria-label="copy.detailsLabel({ employeeName: person.employeeName })"
                @click="emit('select', person.employeeId)"
              >
                <strong>{{ person.employeeName }}</strong> <AppIcon name="arrow" :size="14" />
              </button>
              <small>{{
                copy.employeeDays({ employeeCode: person.employeeCode, days: person.days.length })
              }}</small>
            </td>
            <td :data-label="copy.creditedHours">
              <strong>{{
                person.workMinutes == null ? common.missingValue : minutesLabel(person.workMinutes)
              }}</strong>
              <small v-if="person.incompleteDays" class="report-warning">{{
                copy.incompleteDays({ incompleteDays: person.incompleteDays })
              }}</small>
              <small v-else-if="person.workMinutes != null">{{ copy.calculatedDays }}</small>
              <small v-if="person.noScheduleDays">{{
                copy.noScheduleDays({ noScheduleDays: person.noScheduleDays })
              }}</small>
            </td>
            <td :data-label="copy.leaveDays">
              <span>{{ copy.annualLeaveDays({ days: minutesLabel(person.annualMinutes) }) }}</span>
              <small>{{
                copy.unpaidLeaveDays({ days: minutesLabel(person.unpaidMinutes) })
              }}</small>
            </td>
            <td :data-label="copy.lateEarly">
              <span
                >{{ person.lateMinutes == null ? common.missingValue : `${person.lateMinutes}p` }} /
                {{
                  person.earlyMinutes == null ? common.missingValue : `${person.earlyMinutes}p`
                }}</span
              >
              <small>{{ copy.lateEarlyDays({ lateEarlyDays: person.lateEarlyDays }) }}</small>
            </td>
            <td :data-label="copy.overtime">
              <strong>{{ minutesLabel(person.otCountedMinutes) }}</strong>
              <small>{{
                copy.approvedOvertime({ duration: minutesLabel(person.otApprovedMinutes) })
              }}</small>
            </td>
            <td :data-label="copy.review">
              <span v-if="person.reviewDays" class="report-tag report-tag-warning">{{
                copy.reviewDays({ reviewDays: person.reviewDays })
              }}</span>
              <span v-else class="report-tag">{{ copy.noWarnings }}</span>
              <small v-if="person.issues.record">{{
                copy.invalidRecordDays({ record: person.issues.record })
              }}</small>
              <small v-if="person.issues.conflict">{{
                copy.conflictDays({ conflict: person.issues.conflict })
              }}</small>
              <small v-if="person.pendingRequests">{{
                copy.pendingRequests({ pendingRequests: person.pendingRequests })
              }}</small>
              <small v-if="person.ongoingDays">{{
                copy.ongoingShifts({ ongoingDays: person.ongoingDays })
              }}</small>
            </td>
          </tr>
          <tr v-if="busy || !filtered.length" class="report-empty-row">
            <td colspan="6">
              <div class="attendance-empty" role="status">
                <AppIcon :name="busy ? 'clock' : 'info'" :size="28" />
                <strong>{{ busy ? copy.loading : failed ? copy.loadFailed : copy.empty }}</strong>
                <span>{{ failed ? copy.retryHint : copy.emptyHint }}</span>
                <button v-if="failed && !busy" class="text-button" @click="emit('retry')">
                  {{ copy.reload }}
                </button>
                <button
                  v-else-if="!busy && (search || scope !== 'all')"
                  class="text-button"
                  @click="clearFilters"
                >
                  {{ copy.clearFilters }}
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <p v-if="filtered.length" class="muted small report-count" role="status">
      {{ copy.visibleCount({ visible: filtered.length, total: employees.length }) }}
    </p>
    <PageControls
      v-if="filtered.length"
      :page="page"
      :pages="Math.ceil(filtered.length / 10)"
      :total="filtered.length"
      :busy="busy"
      @change="page = $event"
    />
  </section>
</template>
