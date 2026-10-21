<script setup>
import { useLocalizedMessage } from '@/composables/useLocalizedMessage.js'

import { getDisplayLocale } from '@/i18n/index.js'

import { fieldLimits } from '@/config/validation.js'

import { attendanceRequests as copy } from '@/content/attendance.js'
import { common } from '@/content/common.js'

import { useAsyncResource } from '@/composables/useAsyncResource.js'
import { emptyPage } from '@/api/pagination.js'
import { useIdempotencyKey } from '@/composables/useIdempotencyKey.js'
import { computed, reactive, ref, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { auth } from '@/auth/session.js'
import { attendance } from '@/attendance/api.js'
import { today, dateLabel } from '@/shared/date.js'
import { minutesLabel } from '@/shared/duration.js'
import { attendanceError } from '@/attendance/errors.js'
import { attendanceRoles } from '@/attendance/constants.js'

import { requestBody, requestStatuses, requestTypes } from '@/attendance/requests.js'
import AttendanceRequestScope from '@/components/AttendanceRequestScope.vue'
import AppIcon from '@/components/AppIcon.vue'
import PageControls from '@/components/PageControls.vue'
const props = defineProps({ inbox: Boolean })
const router = useRouter()
const inbox = computed({
  get: () => props.inbox,
  set: (value) => router.push(value ? '/attendance/requests/inbox' : '/attendance/requests'),
})
const defaults = () => ({
  requestType: 'LATE_ARRIVAL',
  workDate: today(),
  period: 'MORNING',
  expectedTime: '',
  reason: '',
})
const form = reactive(defaults()),
  plan = ref(null),
  editing = ref(null),
  cancelId = ref(null),
  filter = ref('')
const message = useLocalizedMessage(),
  mutating = ref(false),
  scheduleBusy = ref(false),
  formElement = ref(null),
  reasonElement = ref(null)
const histories = reactive({})
const {
  data: pagination,
  loading,
  error,
  load,
} = useAsyncResource((page = 0) => attendance.requests(inbox.value, filter.value, page), {
  initialValue: emptyPage,
  errorMessage: attendanceError,
})
const rows = computed(() => pagination.value.content)
const busy = computed(() => loading.value || mutating.value)
const reviewer = computed(() => auth.hasRole(attendanceRoles))
let sequence = 0
onBeforeUnmount(() => {
  sequence++
})
const interval = computed(() =>
  plan.value?.definition?.intervals.find((value) => value.period === form.period),
)
const isLate = computed(() => form.requestType === 'LATE_ARRIVAL')
const timeTitle = computed(() => (isLate.value ? copy.expectedArrival : copy.expectedDeparture))
const duration = computed(() => {
  try {
    return requestBody({ ...form, reason: form.reason || copy.preview }, plan.value)
      .requestedMinutes
  } catch {
    return null
  }
})
const filteredRows = computed(() => rows.value)
const submissionKey = useIdempotencyKey(form)
async function reload() {
  await load()
  if (!error.value && editing.value) {
    const current = rows.value.find((row) => row.id === editing.value.id)
    if (current?.status === 'PENDING') await edit(current)
    else reset()
  }
  await loadSchedule()
}
async function loadSchedule() {
  const ticket = ++sequence,
    date = form.workDate
  plan.value = null
  scheduleBusy.value = false
  if (inbox.value || !date) return
  scheduleBusy.value = true
  try {
    const result = await attendance.schedule(date, date)
    if (ticket !== sequence) return
    plan.value = result[0] || null
    if (!plan.value?.definition) error.value = () => copy.noSchedule
    else if (!plan.value.definition.intervals.some((i) => i.period === form.period))
      form.period = plan.value.definition.intervals[0].period
  } catch (cause) {
    if (ticket === sequence) error.value = () => attendanceError(cause)
  } finally {
    if (ticket === sequence) scheduleBusy.value = false
  }
}
watch(() => form.workDate, loadSchedule)

watch([filter, inbox], () => {
  reset()
  load()
})
watch(inbox, () => {
  message.value = ''
  loadSchedule()
})
function reset() {
  Object.assign(form, defaults())
  editing.value = null
  cancelId.value = null
  error.value = ''
  submissionKey.reset()
}
async function submit() {
  if (busy.value || scheduleBusy.value) return
  error.value = ''
  message.value = ''
  try {
    const body = requestBody(form, plan.value)
    mutating.value = true
    await attendance.saveRequest(
      editing.value?.id,
      body,
      editing.value?.version,
      submissionKey.get(),
    )
    const updated = !!editing.value
    reset()
    await load()
    message.value = () => (updated ? copy.updated : copy.submitted)
  } catch (cause) {
    error.value = () => attendanceError(cause)
  } finally {
    mutating.value = false
  }
}
async function edit(row) {
  Object.assign(form, {
    requestType: row.requestType,
    workDate: row.workDate,
    period: row.period,
    expectedTime: row.expectedTime.slice(0, 5),
    reason: row.reason,
  })
  editing.value = row
  cancelId.value = null
  error.value = ''
  message.value = ''
  await loadSchedule()
  formElement.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  reasonElement.value?.focus({ preventScroll: true })
}
async function cancel(row) {
  mutating.value = true
  error.value = ''
  try {
    await attendance.cancelRequest(row)
    reset()
    await load()
    message.value = () => copy.withdrawn
  } catch (cause) {
    error.value = () => attendanceError(cause)
  } finally {
    mutating.value = false
  }
}
async function decide(row, status) {
  mutating.value = true
  error.value = ''
  try {
    await attendance.decideRequest(row, status, row.decisionNote?.trim() || null)
    await load()
    message.value = () => (status === 'APPROVED' ? copy.approved : copy.rejected)
  } catch (cause) {
    error.value = () => attendanceError(cause)
  } finally {
    mutating.value = false
  }
}
async function showHistory(row) {
  mutating.value = true
  error.value = ''
  try {
    histories[row.id] = (await attendance.requestHistory(row.id)).content
  } catch (cause) {
    error.value = () => attendanceError(cause)
  } finally {
    mutating.value = false
  }
}
onMounted(() => {
  load()
  loadSchedule()
})
</script>

<template>
  <div class="attendance-page">
    <div class="page-heading">
      <div>
        <p class="eyebrow">{{ copy.eyebrow }}</p>
        <h1>{{ inbox ? copy.reviewTitle : copy.title }}</h1>
        <p class="muted">
          {{ inbox ? copy.reviewDescription : copy.description }}
        </p>
      </div>
      <button class="button button-secondary" :disabled="busy || scheduleBusy" @click="reload">
        {{ copy.reload }}
      </button>
    </div>
    <AttendanceRequestScope v-if="reviewer" v-model="inbox" :busy="busy" />
    <p class="info-strip">{{ copy.calculationHint }}</p>
    <p v-if="error" class="alert alert-error" role="alert">{{ error }}</p>
    <p v-if="message" class="info-strip" role="status">{{ message }}</p>
    <div v-if="!inbox" class="attendance-request-layout">
      <form ref="formElement" class="panel create-form" @submit.prevent="submit">
        <div class="attendance-section-heading">
          <div>
            <h2>{{ editing ? copy.editTitle : copy.createTitle }}</h2>
            <p class="muted small">{{ copy.requiredHint }}</p>
          </div>
          <span class="attendance-badge">{{ editing ? copy.editing : copy.newRequest }}</span>
        </div>
        <fieldset class="attendance-request-types" :disabled="busy || scheduleBusy">
          <legend>{{ copy.type }}</legend>
          <label
            v-for="(label, value) in requestTypes"
            :key="value"
            :class="{ selected: form.requestType === value }"
            ><input
              v-model="form.requestType"
              type="radio"
              name="request-type"
              :value="value"
            /><AppIcon :name="value === 'LATE_ARRIVAL' ? 'clock' : 'logout'" /><span>{{
              label
            }}</span></label
          >
        </fieldset>
        <div class="attendance-fields">
          <div class="field">
            <label for="request-date">{{ copy.date }}</label
            ><input
              id="request-date"
              v-model="form.workDate"
              :disabled="busy"
              type="date"
              :min="today()"
              required
            />
          </div>
          <div class="field">
            <label for="request-period">{{ copy.period }}</label
            ><select id="request-period" v-model="form.period" :disabled="busy || scheduleBusy">
              <option
                v-for="item in plan?.definition?.intervals || []"
                :key="item.period"
                :value="item.period"
              >
                {{ item.period === 'MORNING' ? copy.morning : copy.afternoon }} ·
                {{ item.start.slice(0, 5) }}–{{ item.end.slice(0, 5) }}
              </option>
            </select>
          </div>
        </div>
        <div class="attendance-fields">
          <div class="field">
            <label for="request-time">{{ timeTitle }}</label
            ><input
              id="request-time"
              v-model="form.expectedTime"
              :disabled="busy || scheduleBusy"
              type="time"
              required
              aria-describedby="request-time-help"
            />
            <p id="request-time-help" class="field-help">
              {{
                copy.periodHint({
                  start: interval?.start?.slice(0, 5) || common.missingValue,
                  end: interval?.end?.slice(0, 5) || common.missingValue,
                })
              }}
            </p>
          </div>
          <div class="attendance-request-duration">
            <span>{{ isLate ? copy.lateDuration : copy.earlyDuration }}</span
            ><strong>{{ duration == null ? common.missingValue : minutesLabel(duration) }}</strong>
          </div>
        </div>
        <div class="field">
          <label for="request-reason">{{ copy.reason }}</label
          ><textarea
            id="request-reason"
            ref="reasonElement"
            v-model="form.reason"
            :disabled="busy"
            rows="4"
            :maxlength="fieldLimits.attendanceReason"
            required
            :placeholder="copy.reasonPlaceholder"
          ></textarea>
          <p class="field-help attendance-character-count">
            {{ copy.reasonLength({ count: form.reason.length }) }}
          </p>
        </div>

        <div class="button-row">
          <button
            class="button button-primary"
            :disabled="busy || scheduleBusy || !plan?.definition"
          >
            <AppIcon name="check" :size="17" />{{
              editing ? common.saveChanges : copy.submit
            }}</button
          ><button class="button button-secondary" type="button" :disabled="busy" @click="reset">
            {{ editing ? copy.cancelEdit : copy.reset }}
          </button>
        </div>
      </form>
      <aside class="panel create-form attendance-request-aside">
        <p class="eyebrow">{{ copy.summaryTitle }}</p>
        <h2>{{ requestTypes[form.requestType] }}</h2>
        <dl class="attendance-request-summary">
          <div>
            <dt>{{ copy.date }}</dt>
            <dd>{{ dateLabel(form.workDate) || copy.notSelected }}</dd>
          </div>
          <div>
            <dt>{{ copy.referenceShift }}</dt>
            <dd>{{ plan?.definition?.name || copy.noShift }}</dd>
          </div>
          <div>
            <dt>{{ copy.period }}</dt>
            <dd>
              {{ interval?.start?.slice(0, 5) || common.missingValue }}–{{
                interval?.end?.slice(0, 5) || common.missingValue
              }}
            </dd>
          </div>
          <div>
            <dt>{{ timeTitle }}</dt>
            <dd>{{ form.expectedTime || copy.notSelected }}</dd>
          </div>
          <div>
            <dt>{{ copy.requestedDuration }}</dt>
            <dd>{{ duration == null ? common.unknown : minutesLabel(duration) }}</dd>
          </div>
        </dl>
        <div class="attendance-guide-note">
          <AppIcon name="info" :size="18" /><span>{{ copy.durationHint }}</span>
        </div>
        <RouterLink to="/leave" class="text-button attendance-related-link"
          >{{ copy.leaveLink }} <AppIcon name="arrow" :size="16"
        /></RouterLink>
      </aside>
    </div>
    <section class="panel create-form">
      <div class="attendance-section-heading">
        <div>
          <h2>{{ inbox ? copy.inboxTitle : copy.mineTitle }}</h2>
          <p class="muted small">
            {{
              copy.pendingOnPage({ count: rows.filter((row) => row.status === 'PENDING').length })
            }}
          </p>
        </div>
        <span class="attendance-badge">{{ copy.count({ count: filteredRows.length }) }}</span>
      </div>
      <div class="attendance-table-tools">
        <div class="field">
          <label for="request-status">{{ copy.status }}</label
          ><select id="request-status" v-model="filter" :disabled="busy">
            <option value="">{{ copy.allStatuses }}</option>
            <option v-for="(label, value) in requestStatuses" :key="value" :value="value">
              {{ label }}
            </option>
          </select>
        </div>
      </div>
      <div class="table-wrap attendance-request-table" tabindex="0" :aria-label="copy.tableLabel">
        <table>
          <thead>
            <tr>
              <th scope="col">{{ copy.datePeriod }}</th>
              <th scope="col">{{ copy.type }}</th>
              <th scope="col">{{ copy.expectedTime }}</th>
              <th scope="col">{{ copy.duration }}</th>
              <th scope="col">{{ common.status }}</th>
              <th scope="col">{{ copy.details }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in filteredRows" :key="row.id">
              <td class="attendance-date">
                {{ dateLabel(row.workDate)
                }}<small>{{ row.period === 'MORNING' ? copy.morning : copy.afternoon }}</small>
              </td>
              <td>{{ requestTypes[row.requestType] }}</td>
              <td>{{ row.expectedTime }}</td>
              <td>{{ minutesLabel(row.requestedMinutes) }}</td>
              <td>
                <span class="attendance-badge" :class="`request-${row.status.toLowerCase()}`">{{
                  requestStatuses[row.status]
                }}</span>
              </td>
              <td>
                <p v-if="inbox">{{ row.employeeName }} · {{ row.employeeCode }}</p>
                <details class="attendance-request-detail">
                  <summary>{{ copy.reasonAndResponse }}</summary>
                  <p>{{ row.reason }}</p>
                  <p v-if="row.reviewNote">
                    <strong>{{ copy.responseLabel }}</strong> {{ row.reviewNote }}
                  </p>
                  <p v-else class="muted small">
                    {{ row.status === 'CANCELLED' ? copy.withdrawnHint : copy.noResponse }}
                  </p>
                </details>
                <div v-if="row.status === 'PENDING' && !inbox" class="button-row">
                  <button type="button" class="text-button" :disabled="busy" @click="edit(row)">
                    {{ copy.edit }}</button
                  ><button
                    type="button"
                    class="text-button attendance-cancel-link"
                    :disabled="busy"
                    @click="cancelId = row.id"
                  >
                    {{ common.withdraw }}
                  </button>
                </div>
                <div v-if="inbox && row.status === 'PENDING'" class="create-form">
                  <label :for="`decision-${row.id}`">{{ copy.reviewNote }}</label>
                  <textarea
                    :id="`decision-${row.id}`"
                    v-model="row.decisionNote"
                    :maxlength="fieldLimits.attendanceReason"
                    :disabled="busy"
                  ></textarea>
                  <div class="button-row">
                    <button
                      class="button button-primary"
                      type="button"
                      :disabled="busy || row.employeeId === auth.state.user?.employeeId"
                      @click="decide(row, 'APPROVED')"
                    >
                      {{ copy.approve }}
                    </button>
                    <button
                      class="button button-secondary"
                      type="button"
                      :disabled="
                        busy ||
                        !row.decisionNote?.trim() ||
                        row.employeeId === auth.state.user?.employeeId
                      "
                      @click="decide(row, 'REJECTED')"
                    >
                      {{ common.reject }}
                    </button>
                  </div>
                </div>
                <button
                  class="text-button"
                  type="button"
                  :disabled="busy"
                  @click="showHistory(row)"
                >
                  {{ copy.history }}
                </button>
                <ul v-if="histories[row.id]">
                  <li v-for="event in histories[row.id]" :key="event.id">
                    {{ event.action }} ·
                    {{ new Date(event.occurredAt).toLocaleString(getDisplayLocale()) }}
                  </li>
                </ul>
                <div v-if="cancelId === row.id" class="attendance-cancel-confirm">
                  <p>{{ copy.withdrawConfirmation }}</p>
                  <div class="button-row">
                    <button
                      type="button"
                      class="button button-danger-outline"
                      :disabled="busy"
                      @click="cancel(row)"
                    >
                      {{ copy.confirmWithdraw }}</button
                    ><button type="button" class="button button-secondary" @click="cancelId = null">
                      {{ copy.keepRequest }}
                    </button>
                  </div>
                </div>
              </td>
            </tr>
            <tr v-if="!filteredRows.length">
              <td colspan="6">
                <div class="attendance-empty">
                  <AppIcon name="calendar" :size="28" /><strong>{{ copy.empty }}</strong
                  ><span>{{ copy.emptyHint }}</span
                  ><button v-if="filter" type="button" class="text-button" @click="filter = ''">
                    {{ copy.showAll }}
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <PageControls
        :page="pagination.page"
        :pages="pagination.totalPages"
        :total="pagination.totalElements"
        :busy="busy"
        @change="load"
      />
    </section>
  </div>
</template>
