<script setup>
import { useLocalizedMessage } from '@/composables/useLocalizedMessage.js'

import { getDisplayLocale } from '@/i18n/index.js'
import { COMPANY_TIMEZONE } from '@/config/presentation.js'

import { fieldLimits } from '@/config/validation.js'

import { attendanceCorrections as copy } from '@/content/attendance.js'
import { common } from '@/content/common.js'

import { useAsyncResource } from '@/composables/useAsyncResource.js'
import { emptyPage } from '@/api/pagination.js'
import { useIdempotencyKey } from '@/composables/useIdempotencyKey.js'
import { computed, nextTick, onMounted, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { auth } from '@/auth/session.js'
import { attendance } from '@/attendance/api.js'
import { attendanceError } from '@/attendance/errors.js'
import { attendanceRoles } from '@/attendance/constants.js'
import { today } from '@/shared/date.js'
import { shiftLabel } from '@/attendance/shifts.js'
import { correctionBody, localCorrectionTime } from '@/attendance/corrections.js'
import { requestStatuses } from '@/attendance/requests.js'
import AttendanceRequestScope from '@/components/AttendanceRequestScope.vue'
import PageControls from '@/components/PageControls.vue'
const route = useRoute()
const initialDate = /^\d{4}-\d{2}-\d{2}$/.test(route.query.date || '') ? route.query.date : today()
const form = reactive({
  workDate: initialDate,
  proposedCheckIn: '',
  proposedCheckOut: '',
  reason: '',
})
const plan = ref(null),
  day = ref(null),
  editing = ref(null)
const inbox = ref(false),
  filter = ref(''),
  mutating = ref(false),
  dayBusy = ref(false)
const message = useLocalizedMessage(),
  cancelId = ref(null)
const histories = reactive({}),
  notes = reactive({})
const {
  data: pagination,
  loading,
  error,
  load,
} = useAsyncResource((page = 0) => attendance.corrections(inbox.value, filter.value, page), {
  initialValue: emptyPage,
  errorMessage: attendanceError,
})
const rows = computed(() => pagination.value.content)
const busy = computed(() => loading.value || mutating.value)
const reviewer = computed(() => auth.hasRole(attendanceRoles))
const labels = {
  get SUBMITTED() {
    return copy.submit
  },
  get UPDATED() {
    return copy.update
  },
  get CANCELLED() {
    return common.withdraw
  },
  get APPROVED() {
    return common.approve
  },
  get REJECTED() {
    return common.reject
  },
}
let sequence = 0
onBeforeUnmount(() => sequence++)
const stamp = (value) =>
  value
    ? new Date(value).toLocaleString(getDisplayLocale(), { timeZone: COMPANY_TIMEZONE })
    : copy.unrecorded
const submissionKey = useIdempotencyKey(form)
async function reload() {
  await load()
  await loadDay()
}
async function loadDay(fill = false) {
  const ticket = ++sequence,
    date = form.workDate
  plan.value = null
  day.value = null
  if (!date || date > today()) {
    dayBusy.value = false
    return
  }
  dayBusy.value = true
  try {
    const [plans, days] = await Promise.all([
      attendance.schedule(date, date),
      attendance.mine(date, date),
    ])
    if (ticket !== sequence) return
    plan.value = plans[0] || null
    day.value = days[0] || null
    if (fill) {
      form.proposedCheckIn = localCorrectionTime(day.value?.checkIn)
      form.proposedCheckOut = localCorrectionTime(day.value?.checkOut)
    }
  } catch (cause) {
    if (ticket === sequence) error.value = () => attendanceError(cause)
  } finally {
    if (ticket === sequence) dayBusy.value = false
  }
}
watch(
  () => form.workDate,
  () => loadDay(true),
)

watch([inbox, filter], () => {
  editing.value = null
  cancelId.value = null
  load()
})
async function submit() {
  if (busy.value || dayBusy.value) return
  error.value = ''
  message.value = ''
  try {
    const body = correctionBody(form, plan.value, day.value)
    mutating.value = true
    await attendance.saveCorrection(
      editing.value?.id,
      body,
      editing.value?.version,
      submissionKey.get(),
    )
    editing.value = null
    form.reason = ''
    submissionKey.reset()
    await load()
    await loadDay(true)
    message.value = () => copy.saved
  } catch (cause) {
    error.value = () => attendanceError(cause)
  } finally {
    mutating.value = false
  }
}
async function edit(row) {
  editing.value = row
  error.value = ''
  message.value = ''
  // Load the current day before restoring the proposal, including when changing dates.
  form.workDate = row.workDate
  await nextTick()
  await loadDay()
  form.proposedCheckIn = localCorrectionTime(row.proposedCheckIn)
  form.proposedCheckOut = localCorrectionTime(row.proposedCheckOut)
  form.reason = row.reason
}
async function stopEditing() {
  editing.value = null
  form.reason = ''
  await loadDay(true)
}
async function cancel(row) {
  mutating.value = true
  error.value = ''
  message.value = ''
  try {
    await attendance.cancelCorrection(row)
    cancelId.value = null
    if (editing.value?.id === row.id) await stopEditing()
    await load()
    message.value = () => copy.withdrawn
  } catch (cause) {
    error.value = () => attendanceError(cause)
  } finally {
    mutating.value = false
  }
}
async function decide(row, status) {
  error.value = ''
  message.value = ''
  const note = notes[row.id]?.trim() || null
  if (status === 'REJECTED' && !note) {
    error.value = () => copy.rejectionRequired
    return
  }
  mutating.value = true
  try {
    await attendance.decideCorrection(row, status, note)
    delete histories[row.id]
    await load()
    message.value = () => (status === 'APPROVED' ? copy.approved : copy.rejected)
  } catch (cause) {
    error.value = () => attendanceError(cause)
  } finally {
    mutating.value = false
  }
}
async function history(row, page = 0) {
  mutating.value = true
  error.value = ''
  try {
    const result = await attendance.correctionHistory(row.id, page)
    histories[row.id] = {
      ...result,
      content: result.content.map((event) => ({ ...event, data: JSON.parse(event.snapshot) })),
    }
  } catch (cause) {
    error.value = () => attendanceError(cause)
  } finally {
    mutating.value = false
  }
}
onMounted(() => {
  load()
  loadDay(true)
})
</script>
<template>
  <div class="attendance-page">
    <div class="page-heading">
      <div>
        <p class="eyebrow">{{ copy.eyebrow }}</p>
        <h1>{{ copy.title }}</h1>
        <p class="muted">{{ copy.description }}</p>
      </div>
      <button class="button button-secondary" :disabled="busy || dayBusy" @click="reload">
        {{ common.reload }}
      </button>
    </div>
    <AttendanceRequestScope v-if="reviewer" v-model="inbox" :busy="busy" />
    <p v-if="error" class="alert alert-error" role="alert">{{ error }}</p>
    <p v-if="message" class="info-strip" role="status">{{ message }}</p>
    <form v-if="!inbox" class="panel create-form" @submit.prevent="submit">
      <h2>{{ editing ? copy.editTitle : copy.createTitle }}</h2>
      <p class="muted">{{ copy.formHint }}</p>
      <fieldset :disabled="busy || dayBusy" class="attendance-correction-fields">
        <div class="attendance-fields">
          <div class="field">
            <label for="correction-date">{{ copy.workDate }}</label
            ><input
              id="correction-date"
              v-model="form.workDate"
              type="date"
              :max="today()"
              required
            />
          </div>
          <div class="field">
            <label for="correction-in">{{ copy.requestedCheckIn }}</label
            ><input
              id="correction-in"
              v-model="form.proposedCheckIn"
              type="datetime-local"
              step="1"
              required
            />
          </div>
          <div class="field">
            <label for="correction-out">{{ copy.requestedCheckOut }}</label
            ><input
              id="correction-out"
              v-model="form.proposedCheckOut"
              type="datetime-local"
              step="1"
              required
            />
          </div>
        </div>
        <p v-if="dayBusy" class="muted">{{ copy.loadingDay }}</p>
        <template v-else-if="plan?.definition">
          <p class="muted">{{ plan.definition.name }} · {{ shiftLabel(plan.definition) }}</p>
          <p>
            {{
              copy.currentTimes({ checkIn: stamp(day?.checkIn), checkOut: stamp(day?.checkOut) })
            }}
          </p>
        </template>
        <p v-else class="muted">{{ copy.noSchedule }}</p>
        <div class="field">
          <label for="correction-reason">{{ copy.reason }}</label
          ><textarea
            id="correction-reason"
            v-model="form.reason"
            :maxlength="fieldLimits.attendanceReason"
            rows="3"
            required
          />
        </div>
        <div class="button-row">
          <button class="button button-primary" :disabled="dayBusy || !plan?.definition">
            {{ editing ? common.saveChanges : copy.submit }}
          </button>
          <button v-if="editing" type="button" class="button button-secondary" @click="stopEditing">
            {{ copy.cancelEdit }}
          </button>
        </div>
      </fieldset>
    </form>
    <section class="panel create-form" :aria-busy="busy">
      <h2>{{ inbox ? copy.inboxTitle : copy.mineTitle }}</h2>
      <div class="field">
        <label for="correction-status">{{ copy.status }}</label
        ><select id="correction-status" v-model="filter" :disabled="busy">
          <option value="">{{ common.all }}</option>
          <option v-for="(label, value) in requestStatuses" :key="value" :value="value">
            {{ label }}
          </option>
        </select>
      </div>
      <p v-if="busy" class="muted">{{ copy.processing }}</p>
      <p v-else-if="!rows.length" class="muted">{{ copy.empty }}</p>
      <article v-for="row in rows" :key="row.id" class="panel create-form">
        <h3>
          {{ row.workDate }} · {{ row.employeeName }}
          <span class="attendance-badge">{{ requestStatuses[row.status] }}</span>
        </h3>
        <p>
          {{
            copy.originalTimes({
              checkIn: stamp(row.beforeCheckIn),
              checkOut: stamp(row.beforeCheckOut),
            })
          }}
        </p>
        <p>
          <strong>{{
            copy.requestedTimes({
              checkIn: stamp(row.proposedCheckIn),
              checkOut: stamp(row.proposedCheckOut),
            })
          }}</strong>
        </p>
        <p>{{ copy.reasonSummary({ reason: row.reason }) }}</p>
        <p v-if="row.reviewedAt" class="muted">
          {{ copy.reviewedAt({ reviewer: row.reviewedBy, date: stamp(row.reviewedAt) }) }}
        </p>
        <p v-if="row.reviewNote">{{ copy.reviewNote({ reviewNote: row.reviewNote }) }}</p>
        <div
          v-if="inbox && row.status === 'PENDING' && row.employeeId !== auth.state.user?.employeeId"
          class="create-form"
        >
          <div class="field">
            <label :for="`review-${row.id}`">{{ copy.reviewNoteLabel }}</label
            ><textarea
              :id="`review-${row.id}`"
              v-model="notes[row.id]"
              :maxlength="fieldLimits.attendanceReason"
              :disabled="busy"
            />
          </div>
          <div class="button-row">
            <button class="button button-primary" :disabled="busy" @click="decide(row, 'APPROVED')">
              {{ copy.approve }}</button
            ><button
              class="button button-danger-outline"
              :disabled="busy"
              @click="decide(row, 'REJECTED')"
            >
              {{ common.reject }}
            </button>
          </div>
        </div>
        <div class="button-row">
          <template v-if="!inbox && row.status === 'PENDING'"
            ><button class="button button-secondary" :disabled="busy || dayBusy" @click="edit(row)">
              {{ copy.edit }}</button
            ><button
              class="button button-danger-outline"
              :disabled="busy"
              @click="cancelId = row.id"
            >
              {{ common.withdraw }}
            </button></template
          >
          <button class="text-button" :disabled="busy" @click="history(row)">
            {{ copy.history }}
          </button>
        </div>
        <div v-if="cancelId === row.id" class="info-strip">
          <p>{{ copy.withdrawConfirmation }}</p>
          <button class="button button-danger-outline" :disabled="busy" @click="cancel(row)">
            {{ copy.confirmWithdraw }}
          </button>
          <button class="button button-secondary" :disabled="busy" @click="cancelId = null">
            {{ copy.keepRequest }}
          </button>
        </div>
        <template v-if="histories[row.id]">
          <ul>
            <li v-for="event in histories[row.id].content" :key="event.id">
              {{ labels[event.action] || event.action }} · {{ stamp(event.occurredAt) }} ·
              {{ event.actorUserId }}
              <p>
                {{ stamp(event.data.beforeCheckIn) }} → {{ stamp(event.data.beforeCheckOut) }} ⇒
                {{ stamp(event.data.proposedCheckIn) }} → {{ stamp(event.data.proposedCheckOut) }}
              </p>
              <p>
                {{ event.data.reason
                }}<span v-if="event.data.reviewNote"> · {{ event.data.reviewNote }}</span>
              </p>
            </li>
          </ul>
          <PageControls
            :page="histories[row.id].page"
            :pages="histories[row.id].totalPages"
            :total="histories[row.id].totalElements"
            :busy="busy"
            @change="history(row, $event)"
          />
        </template>
      </article>
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
<style scoped>
.attendance-correction-fields {
  border: 0;
  padding: 0;
  margin: 0;
  min-width: 0;
  display: grid;
  gap: 1rem;
}
</style>
